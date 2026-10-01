package com.minimaldesigner.arise.data

import android.net.Uri
import androidx.room.withTransaction
import com.minimaldesigner.arise.core.Angle
import com.minimaldesigner.arise.core.ChallengeInfo
import com.minimaldesigner.arise.core.freeSleepDay
import com.minimaldesigner.arise.core.stored
import com.minimaldesigner.arise.core.withDerivedTicks
import com.minimaldesigner.arise.core.DayRecord
import com.minimaldesigner.arise.core.MetaJson
import com.minimaldesigner.arise.core.MetaKey
import com.minimaldesigner.arise.core.FoodGoals
import com.minimaldesigner.arise.core.Pause
import com.minimaldesigner.arise.core.decodePause
import com.minimaldesigner.arise.core.encodePause
import com.minimaldesigner.arise.core.PhotoPins
import com.minimaldesigner.arise.core.Profile
import com.minimaldesigner.arise.core.ReadLog
import com.minimaldesigner.arise.core.Reading
import com.minimaldesigner.arise.core.PhotoMeta
import com.minimaldesigner.arise.core.PhotoSource
import com.minimaldesigner.arise.core.Days
import com.minimaldesigner.arise.core.ProgramId
import com.minimaldesigner.arise.core.Run
import com.minimaldesigner.arise.core.TaskDef
import com.minimaldesigner.arise.core.TaskKind
import com.minimaldesigner.arise.core.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A photo's metadata plus where its files are. */
data class Photo(val meta: PhotoMeta, val file: File, val thumb: File)

/** An ended or restarted attempt, for Profile → Past attempts. */
data class PastRun(
    val program: ProgramId, val attempt: Int, val start: LocalDate, val end: LocalDate?, val cleared: Int, val restarted: Boolean,
    /** Restarted after a break (0.2.1-alpha.3). */
    val paused: Boolean = false,
)

/** Profile, the book being read, the pinned photos, when the challenge was set up and any break (the `meta` table). */
data class AppMeta(
    val profile: Profile = Profile(),
    val reading: Reading = Reading(),
    val pins: PhotoPins = PhotoPins(),
    val challenge: ChallengeInfo = ChallengeInfo(),
    val pause: Pause? = null,
    val foodGoals: FoodGoals = FoodGoals(),
) {
    /** The day the live challenge was set up or last restarted, if known. */
    val startedOn: LocalDate? get() = challenge.startedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun profileThumb(store: PhotoStore): File? = profile.photo?.let { store.file("${it}_t.jpg") }
}

class ChallengeRepository(private val db: AriseDb, private val store: PhotoStore) {
    private val dao = db.challenge()

    val photos: Flow<List<Photo>> = dao.observePhotos().map { rows ->
        rows.map { r ->
            Photo(
                PhotoMeta(r.id, LocalDate.parse(r.date), Angle.of(r.angle), PhotoSource.of(r.source), r.createdAt),
                store.file(r.file), store.file(r.thumb),
            )
        }
    }

    /** Stores the image at [uri] and records it. Returns the new photo's id. */
    suspend fun addPhoto(uri: Uri, date: LocalDate, angle: Angle, source: PhotoSource): String {
        val id = UUID.randomUUID().toString()
        val saved = store.save(uri, id)
        try {
            dao.insertPhoto(PhotoEntity(id, date.toString(), angle.key, saved.file, saved.thumb, source.key, Instant.now().toString()))
        } catch (e: Exception) {
            store.delete(saved.file, saved.thumb)
            throw e
        }
        return id
    }

    suspend fun setPhotoAngle(id: String, angle: Angle) = dao.setPhotoAngle(id, angle.key)

    suspend fun deletePhoto(id: String) {
        val row = dao.photo(id) ?: return
        dao.deletePhoto(id)
        store.delete(row.file, row.thumb)
    }

    /** The active challenge, or null when none is running. */
    val run: Flow<Run?> = dao.observeActive().map { it?.toRun() }

    val days: Flow<Days> = combine(dao.observeLogs(), dao.observeWorkouts(), dao.observeReads()) { logs, workouts, reads ->
        val done = logs.groupBy { it.date }
        val wk = workouts.groupBy { it.date }
        val rd = reads.associateBy { it.date }
        (done.keys + wk.keys + rd.keys).associate { k ->
            LocalDate.parse(k) to DayRecord(
                done = done[k].orEmpty().associate { it.taskId to it.doneAt },
                workouts = wk[k].orEmpty().map { Workout(it.taskId, it.type, it.mins, it.outdoor) },
                read = rd[k]?.let { ReadLog(it.title, it.page, it.total, it.pages) },
            )
        }
    }

    val history: Flow<List<PastRun>> = dao.observeHistory().map { rows ->
        rows.map { r ->
            PastRun(ProgramId.of(r.program), r.attempt, LocalDate.parse(r.startDate), r.endDate?.let(LocalDate::parse), r.cleared ?: 0, r.endReason == "missed" || r.endReason == "paused", r.endReason == "paused")
        }
    }

    suspend fun rename(name: String) = dao.rename(name)

    val meta: Flow<AppMeta> = dao.observeMeta().map { rows ->
        val m = rows.associate { it.key to it.value }
        AppMeta(
            MetaJson.decode(Profile.serializer(), m[MetaKey.PROFILE.key], Profile()),
            MetaJson.decode(Reading.serializer(), m[MetaKey.READING.key], Reading()),
            MetaJson.decode(PhotoPins.serializer(), m[MetaKey.PINS.key], PhotoPins()),
            MetaJson.decode(ChallengeInfo.serializer(), m[MetaKey.CHALLENGE.key], ChallengeInfo()),
            decodePause(m[MetaKey.PAUSE.key]),
            MetaJson.decode(FoodGoals.serializer(), m[MetaKey.FOOD_GOALS.key], FoodGoals()),
        )
    }

    /**
     * [days] plus the ticks that follow from other data: the photo task on any day with a
     * progress photo, and Day 1's sleep when it's free. Everything that scores days reads this.
     * It has to come after [meta]: property initialisers run in order.
     */
    val liveDays: Flow<Days> = combine(days, run, photos, meta) { d, r, p, m ->
        withDerivedTicks(r, d, p.map { it.meta.date }.toSet(), freeSleepDay(r, m.startedOn))
    }

    suspend fun setProfile(p: Profile) = dao.upsertMeta(MetaEntity(MetaKey.PROFILE.key, MetaJson.encode(Profile.serializer(), p)))
    suspend fun setReading(r: Reading) = dao.upsertMeta(MetaEntity(MetaKey.READING.key, MetaJson.encode(Reading.serializer(), r)))
    suspend fun setPins(p: PhotoPins) = dao.upsertMeta(MetaEntity(MetaKey.PINS.key, MetaJson.encode(PhotoPins.serializer(), p)))
    suspend fun setFoodGoals(g: FoodGoals) = dao.upsertMeta(MetaEntity(MetaKey.FOOD_GOALS.key, MetaJson.encode(FoodGoals.serializer(), g)))

    /** Starts, moves or (null) clears the break. */
    suspend fun setPause(p: Pause?) =
        if (p == null) dao.deleteMeta(MetaKey.PAUSE.key) else dao.upsertMeta(MetaEntity(MetaKey.PAUSE.key, encodePause(p)))

    private suspend fun setStartedOn(d: LocalDate) =
        dao.upsertMeta(MetaEntity(MetaKey.CHALLENGE.key, MetaJson.encode(ChallengeInfo.serializer(), ChallengeInfo(d.toString()))))

    /** Stores a new profile photo (square crop happens on screen) and drops the old one. */
    suspend fun setProfilePhoto(uri: Uri, current: Profile) {
        val id = "profile-${System.currentTimeMillis()}"
        store.save(uri, id)
        setProfile(current.copy(photo = id))
        current.photo?.let { store.delete("$it.jpg", "${it}_t.jpg") }
    }

    suspend fun start(run: Run, today: LocalDate) = db.withTransaction {
        dao.deactivateAll()
        val id = dao.insertRun(run.toEntity())
        dao.insertTasks(run.tasks.mapIndexed { i, t -> t.toEntity(id, i) })
        setStartedOn(today)
    }

    /** Replaces everything stored for one calendar day. Derived ticks (a photo, a free night) aren't stored. */
    suspend fun setDay(date: LocalDate, record: DayRecord) = db.withTransaction {
        val day = record.stored()
        val k = date.toString()
        dao.clearLogs(k)
        dao.clearWorkouts(k)
        dao.insertLogs(day.done.map { (id, at) -> DayLogEntity(k, id, at) })
        dao.insertWorkouts(day.workouts.map { WorkoutEntity(k, it.taskId, it.type, it.mins, it.outdoor) })
        dao.clearRead(k)
        day.read?.let { dao.upsertRead(ReadEntity(k, it.title, it.page, it.total, it.pages)) }
    }

    /**
     * Restart after a miss ([reason] "missed") or a break ("paused"): keep the attempt as
     * history and move the live run to a new Day 1. Any break is over.
     */
    suspend fun restart(next: Run, today: LocalDate, cleared: Int, reason: String = "missed") = db.withTransaction {
        val live = dao.active() ?: return@withTransaction
        dao.insertRun(live.copy(id = 0, active = false, endDate = today.toString(), endReason = reason, cleared = cleared))
        dao.upsertRun(live.copy(startDate = next.startDate.toString(), attempt = next.attempt))
        setStartedOn(today)
        dao.deleteMeta(MetaKey.PAUSE.key)
    }

    suspend fun end(today: LocalDate, cleared: Int) = db.withTransaction {
        val live = dao.active() ?: return@withTransaction
        dao.upsertRun(live.copy(active = false, endDate = today.toString(), endReason = "ended", cleared = cleared))
        dao.deleteMeta(MetaKey.PAUSE.key)
    }
}

private fun RunWithTasks.toRun() = Run(
    program = ProgramId.of(run.program),
    name = run.name,
    startDate = LocalDate.parse(run.startDate),
    lengthDays = run.lengthDays,
    resetOnMiss = run.resetOnMiss,
    tasks = tasks.sortedBy { it.position }.map { TaskDef(it.taskId, it.label, it.sub, TaskKind.of(it.kind), it.outdoor) },
    attempt = run.attempt,
)

private fun Run.toEntity() = RunEntity(
    program = program.key, name = name, startDate = startDate.toString(), lengthDays = lengthDays,
    resetOnMiss = resetOnMiss, attempt = attempt, active = true,
)

private fun TaskDef.toEntity(runId: Long, position: Int) =
    TaskEntity(runId, id, position, label, sub, kind.key, outdoor)
