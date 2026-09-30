// Mindfulness sessions are still marked experimental in connect-client 1.1.0.
@file:OptIn(ExperimentalMindfulnessSessionApi::class)

package com.minimaldesigner.arise.data

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.minimaldesigner.arise.core.HealthData
import com.minimaldesigner.arise.core.HealthMindful
import com.minimaldesigner.arise.core.HealthSleep
import com.minimaldesigner.arise.core.HealthWorkout
import com.minimaldesigner.arise.core.WeightPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/** What ARISE reads, one Health Connect permission each. */
enum class HealthKind(val label: String, val record: KClass<out Record>, private val fixedPermission: String? = null) {
    WEIGHT("Weight", WeightRecord::class),
    HEIGHT("Height", HeightRecord::class),
    WORKOUTS("Workouts", ExerciseSessionRecord::class),
    SLEEP("Sleep", SleepSessionRecord::class),
    STEPS("Steps", StepsRecord::class),
    /** Only where the phone's Health Connect supports it (see [HealthState.supported]). */
    MINDFULNESS("Mindfulness", MindfulnessSessionRecord::class, "android.permission.health.READ_MINDFULNESS"),
    ;

    val permission: String get() = fixedPermission ?: HealthPermission.getReadPermission(record)
}

enum class HealthAccess {
    /** Checking, before the first answer. */
    UNKNOWN,
    /** No Health Connect on this phone. */
    UNAVAILABLE,
    /** Health Connect needs installing or updating from the Play Store. */
    NEEDS_UPDATE,
    /** Available, nothing granted yet. */
    OFF,
    /** At least one kind granted. */
    ON,
}

data class HealthState(
    val access: HealthAccess = HealthAccess.UNKNOWN,
    val granted: Set<HealthKind> = emptySet(),
    /** The kinds this phone's Health Connect can share (mindfulness needs a recent version). */
    val supported: Set<HealthKind> = HealthKind.entries.toSet() - HealthKind.MINDFULNESS,
    val data: HealthData? = null,
    val readAt: Long? = null,
    val reading: Boolean = false,
    val error: String? = null,
) {
    val on: Boolean get() = access == HealthAccess.ON
}

/**
 * Reads weight, height, workouts, sleep, steps and mindfulness from Health Connect. Nothing is copied
 * into Room: Health Connect is already on the phone, so each refresh reads it again.
 */
class HealthRepository(private val context: Context) {
    private val _state = MutableStateFlow(HealthState())
    val state: StateFlow<HealthState> = _state.asStateFlow()
    private val lock = Mutex()
    private var lastRead = 0L

    private var cached: HealthConnectClient? = null

    /** Null until Health Connect is available (it can be installed while ARISE runs). */
    private fun client(): HealthConnectClient? = cached
        ?: if (sdkStatus() == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context).also { cached = it } else null

    private fun sdkStatus() = HealthConnectClient.getSdkStatus(context)

    private fun hasFeature(feature: Int): Boolean = runCatching {
        client()?.features?.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    /** The kinds this phone's Health Connect can share. */
    private fun supported(): Set<HealthKind> =
        if (hasFeature(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION)) HealthKind.entries.toSet()
        else HealthKind.entries.toSet() - HealthKind.MINDFULNESS

    /** Every supported read permission, plus history (data older than 30 days) where the phone supports it. */
    fun permissions(): Set<String> {
        val base = supported().map { it.permission }.toSet()
        val history = hasFeature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)
        return if (history) base + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY else base
    }

    /** Health Connect's own screen, where the user can see and change what ARISE may read. */
    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    /** The Play Store page for Health Connect (Android 13 and older, where it's an app). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = android.net.Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }

    /**
     * Checks access and, if anything is granted, reads it again. Unless [force], does
     * nothing when the last read was under [STALE_MS] ago (Health Connect rate-limits reads).
     */
    suspend fun refresh(force: Boolean, runStart: LocalDate?) {
        val status = sdkStatus()
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            val access = if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) HealthAccess.NEEDS_UPDATE else HealthAccess.UNAVAILABLE
            _state.update { HealthState(access = access) }
            return
        }
        val c = client() ?: return
        if (!lock.tryLock()) return
        try {
            val granted = try {
                c.permissionController.getGrantedPermissions()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't reach Health Connect.") }
                return
            }
            val supported = supported()
            val kinds = supported.filter { it.permission in granted }.toSet()
            if (kinds.isEmpty()) {
                _state.update { HealthState(access = HealthAccess.OFF, supported = supported) }
                return
            }
            val newlyGranted = kinds != _state.value.granted
            _state.update { it.copy(access = HealthAccess.ON, granted = kinds, supported = supported) }
            if (!force && !newlyGranted && System.currentTimeMillis() - lastRead < STALE_MS) return
            lastRead = System.currentTimeMillis()
            _state.update { it.copy(reading = true) }
            val (data, failed) = read(c, kinds, runStart)
            _state.update {
                it.copy(
                    data = data, readAt = System.currentTimeMillis(), reading = false,
                    error = if (failed.isEmpty()) null else "Couldn't read ${failed.joinToString(", ") { k -> k.label.lowercase() }} from Health Connect.",
                )
            }
        } finally {
            lock.unlock()
        }
    }

    private suspend fun read(c: HealthConnectClient, kinds: Set<HealthKind>, runStart: LocalDate?): Pair<HealthData, List<HealthKind>> {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val failed = mutableListOf<HealthKind>()
        suspend fun <T> kind(k: HealthKind, empty: T, block: suspend () -> T): T {
            if (k !in kinds) return empty
            return try {
                block()
            } catch (e: Exception) {
                failed += k
                empty
            }
        }

        // A year of weights, or back to Day 1 if the challenge is older; the start weight needs it.
        val weightFrom = listOfNotNull(today.minusYears(1), runStart?.minusDays(7)).min().atStartOfDay(zone).toInstant()
        val weights = kind(HealthKind.WEIGHT, emptyList()) {
            readAll(c, WeightRecord::class, TimeRangeFilter.between(weightFrom, now))
                .map { WeightPoint(it.time.atZone(zone).toLocalDate(), it.weight.inKilograms) to it.time }
                .groupBy { it.first.date }
                .map { (_, day) -> day.minBy { it.second }.first }
                .sortedBy { it.date }
        }
        val height = kind(HealthKind.HEIGHT, null) {
            c.readRecords(ReadRecordsRequest(HeightRecord::class, TimeRangeFilter.before(now), ascendingOrder = false, pageSize = 1))
                .records.firstOrNull()?.height?.inMeters?.let { Math.round(it * 1000) / 10.0 }
        }
        val recent = TimeRangeFilter.between(now.minus(RECENT_DAYS, ChronoUnit.DAYS), now)
        val workouts = kind(HealthKind.WORKOUTS, emptyList()) {
            readAll(c, ExerciseSessionRecord::class, recent).map { r ->
                HealthWorkout(
                    id = r.metadata.id,
                    start = LocalDateTime.ofInstant(r.startTime, zone),
                    end = LocalDateTime.ofInstant(r.endTime, zone),
                    type = workoutType(r.exerciseType),
                    outdoor = r.exerciseType in OUTDOOR,
                    title = r.title?.takeIf { it.isNotBlank() },
                )
            }.sortedBy { it.start }
        }
        val sleeps = kind(HealthKind.SLEEP, emptyList()) {
            readAll(c, SleepSessionRecord::class, recent).map { r ->
                val total = ChronoUnit.MINUTES.between(r.startTime, r.endTime)
                val awake = r.stages.filter { it.stage in AWAKE }.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
                HealthSleep(LocalDateTime.ofInstant(r.startTime, zone), LocalDateTime.ofInstant(r.endTime, zone), (total - awake).toInt().coerceAtLeast(0))
            }.sortedBy { it.start }
        }
        val steps = kind(HealthKind.STEPS, emptyMap()) {
            // Aggregated, so a phone and a watch counting the same steps aren't added twice.
            val from = today.minusDays(RECENT_DAYS - 1).atStartOfDay()
            c.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from, LocalDateTime.now(zone)), Period.ofDays(1)),
            ).mapNotNull { g -> g.result[StepsRecord.COUNT_TOTAL]?.let { g.startTime.toLocalDate() to it } }.toMap()
        }
        val mindful = kind(HealthKind.MINDFULNESS, emptyList()) {
            readAll(c, MindfulnessSessionRecord::class, recent).map { r ->
                HealthMindful(LocalDateTime.ofInstant(r.startTime, zone), LocalDateTime.ofInstant(r.endTime, zone), r.title?.takeIf { it.isNotBlank() })
            }.sortedBy { it.start }
        }
        return HealthData(height, weights, workouts, sleeps, steps, mindful) to failed
    }

    /** Every page of a read. */
    private suspend fun <T : Record> readAll(c: HealthConnectClient, type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val page = c.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
            out += page.records
            token = page.pageToken?.takeIf { it.isNotEmpty() }
        } while (token != null && out.size < 10_000)
        return out
    }

    companion object {
        /** The Health Connect app on Android 13 and older (the library's own constant is internal). */
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

        /** How old a read can get before opening the app reads again. */
        const val STALE_MS = 5 * 60 * 1000L
        /** Days of workouts, sleep and steps to read. */
        const val RECENT_DAYS = 30L

        private val AWAKE = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        )

        private val OUTDOOR = setOf(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER, ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
            ExerciseSessionRecord.EXERCISE_TYPE_PADDLING, ExerciseSessionRecord.EXERCISE_TYPE_SKIING,
            ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING, ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING,
            ExerciseSessionRecord.EXERCISE_TYPE_SURFING, ExerciseSessionRecord.EXERCISE_TYPE_SAILING,
            ExerciseSessionRecord.EXERCISE_TYPE_GOLF,
        )

        /** Health Connect's exercise type as one of the Workout sheet's types. */
        fun workoutType(t: Int): String = when (t) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Run"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING -> "Walk"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Cycle"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Strength"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP,
            ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
            ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swim"
            else -> "Sport"
        }
    }
}
