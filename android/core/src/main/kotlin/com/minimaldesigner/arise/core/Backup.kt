package com.minimaldesigner.arise.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// An ARISE backup is a zip: `manifest.json` (below) plus `photos/<id>.jpg` for every photo.
// Dates are ISO strings, as in the database, so the file stays readable.

const val BACKUP_FORMAT = "arise-backup"
/** 2 adds reading logs, [BackupMeta] and the profile photo. Version 1 backups still restore. */
const val BACKUP_VERSION = 2
const val BACKUP_MANIFEST = "manifest.json"
const val BACKUP_PROFILE_PHOTO = "profile.jpg"

@Serializable
data class BackupTask(val id: String, val label: String, val sub: String = "", val kind: String, val outdoor: Boolean = false)

/** A challenge, live or history. [id] only links rows inside one backup. */
@Serializable
data class BackupRun(
    val id: Long,
    val program: String,
    val name: String,
    val startDate: String,
    val lengthDays: Int,
    val resetOnMiss: Boolean,
    val attempt: Int,
    val active: Boolean,
    val endDate: String? = null,
    val endReason: String? = null,
    val cleared: Int? = null,
    val tasks: List<BackupTask> = emptyList(),
)

@Serializable
data class BackupWorkout(val taskId: String, val type: String, val mins: Int, val outdoor: Boolean)

@Serializable
data class BackupRead(val title: String, val page: Int, val total: Int = 0, val pages: Int = 0)

@Serializable
data class BackupDay(val date: String, val done: Map<String, String> = emptyMap(), val workouts: List<BackupWorkout> = emptyList(), val read: BackupRead? = null)

/** Profile, reading, pins and (0.2.1) the challenge's set-up day. [Profile.photo] is ignored: the photo travels as `profile.jpg`. */
@Serializable
data class BackupMeta(
    val profile: Profile = Profile(),
    val reading: Reading = Reading(),
    val pins: PhotoPins = PhotoPins(),
    val hasProfilePhoto: Boolean = false,
    val challenge: ChallengeInfo = ChallengeInfo(),
    /** A break in progress (0.2.1-alpha.3), or null. */
    val pause: Pause? = null,
    /** Daily food targets (1.2.0). */
    val foodGoals: FoodGoals = FoodGoals(),
)

@Serializable
data class BackupPhoto(val id: String, val date: String, val angle: String, val source: String, val createdAt: String) {
    /** Where the image sits inside the zip. */
    val entry: String get() = "photos/$id.jpg"
}

@Serializable
data class Backup(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    val createdAt: String,
    val app: String = "",
    val runs: List<BackupRun> = emptyList(),
    val days: List<BackupDay> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
    val meta: BackupMeta = BackupMeta(),
)

class BadBackup(message: String) : Exception(message)

object BackupJson {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(b: Backup): String = json.encodeToString(Backup.serializer(), b)

    /** Parses and checks a manifest, or throws [BadBackup] with a line for the user. */
    fun decode(text: String): Backup {
        val b = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: Exception) {
            throw BadBackup("This zip's manifest isn't an ARISE backup.")
        }
        if (b.format != BACKUP_FORMAT) throw BadBackup("This zip isn't an ARISE backup.")
        if (b.version > BACKUP_VERSION) throw BadBackup("This backup is from a newer ARISE. Update the app first.")
        if (b.runs.count { it.active } > 1) throw BadBackup("This backup has more than one live challenge.")
        val ids = b.runs.map { it.id }.toSet()
        if (ids.size != b.runs.size) throw BadBackup("This backup repeats a challenge id.")
        try {
            (b.runs.map { it.startDate } + b.days.map { it.date } + b.photos.map { it.date }).forEach(LocalDate::parse)
        } catch (e: Exception) {
            throw BadBackup("This backup has a date ARISE can't read.")
        }
        if (b.photos.map { it.id }.toSet().size != b.photos.size) throw BadBackup("This backup repeats a photo.")
        if (b.photos.any { !SAFE_ID.matches(it.id) }) throw BadBackup("This backup has a photo with an unsafe name.")
        return b
    }

    /** Photo ids become file names, so keep them to plain characters. */
    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
}

/** One photo in the old 75 Hard app's backup: `photos/day-<N>-<epoch ms>.jpg`. */
data class Hard75Photo(val day: Int, val takenAt: Instant) {
    /** The ARISE photo id, stable across imports, so importing twice adds nothing. */
    val id: String get() = "75h-${takenAt.toEpochMilli()}"

    /** The calendar day it was taken, in [zone]. */
    fun date(zone: ZoneId): LocalDate = takenAt.atZone(zone).toLocalDate()
}

private val HARD75_NAME = Regex("""(?:.*/)?day-(\d{1,3})-(\d{12,14})\.(?:jpe?g|png)""", RegexOption.IGNORE_CASE)

/**
 * Parses a zip entry name from the 75 Hard backup, or null for anything else. `N` counts
 * from 1 again after every restart, so only the timestamp gives the real date.
 */
fun parseHard75Photo(entry: String): Hard75Photo? {
    val m = HARD75_NAME.matchEntire(entry) ?: return null
    return Hard75Photo(m.groupValues[1].toInt(), Instant.ofEpochMilli(m.groupValues[2].toLong()))
}
