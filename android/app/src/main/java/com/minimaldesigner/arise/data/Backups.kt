package com.minimaldesigner.arise.data

import androidx.room.withTransaction
import com.minimaldesigner.arise.core.Angle
import com.minimaldesigner.arise.core.BACKUP_MANIFEST
import com.minimaldesigner.arise.core.BACKUP_PROFILE_PHOTO
import com.minimaldesigner.arise.core.BackupMeta
import com.minimaldesigner.arise.core.ChallengeInfo
import com.minimaldesigner.arise.core.BackupRead
import com.minimaldesigner.arise.core.MetaJson
import com.minimaldesigner.arise.core.MetaKey
import com.minimaldesigner.arise.core.FoodGoals
import com.minimaldesigner.arise.core.decodePause
import com.minimaldesigner.arise.core.encodePause
import com.minimaldesigner.arise.core.PhotoPins
import com.minimaldesigner.arise.core.Profile
import com.minimaldesigner.arise.core.Reading
import com.minimaldesigner.arise.core.Backup
import com.minimaldesigner.arise.core.BackupDay
import com.minimaldesigner.arise.core.BackupJson
import com.minimaldesigner.arise.core.BackupPhoto
import com.minimaldesigner.arise.core.BackupRun
import com.minimaldesigner.arise.core.BackupTask
import com.minimaldesigner.arise.core.BackupWorkout
import com.minimaldesigner.arise.core.BadBackup
import com.minimaldesigner.arise.core.PhotoSource
import com.minimaldesigner.arise.core.parseHard75Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a 75 Hard import did. [failed] are photos whose image couldn't be read. */
data class Hard75Import(val added: Int, val already: Int, val failed: Int, val other: List<String>)

/**
 * Backup and restore of everything ARISE stores (the zip format is in :core Backup.kt),
 * plus the photo import from the old 75 Hard app's backup zip.
 */
class Backups(private val db: AriseDb, private val store: PhotoStore, private val scratch: File) {
    private val dao = db.challenge()

    /** Writes a backup of every challenge, day and photo to [out]. Returns how many photos went in. */
    suspend fun export(out: OutputStream, app: String): Int = withContext(Dispatchers.IO) {
        val b = db.withTransaction { snapshot(app) }
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry(BACKUP_MANIFEST))
            z.write(BackupJson.encode(b).toByteArray())
            z.closeEntry()
            val files = dao.allPhotos().associate { it.id to it.file }
            b.photos.forEach { p ->
                z.putNextEntry(ZipEntry(p.entry))
                store.file(files.getValue(p.id)).inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
            if (b.meta.hasProfilePhoto) {
                z.putNextEntry(ZipEntry(BACKUP_PROFILE_PHOTO))
                store.file("${profileId()}.jpg").inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        b.photos.size
    }

    /** The stored profile photo's id (readMeta() blanks it for the manifest). */
    private suspend fun profileId(): String? =
        MetaJson.decode(Profile.serializer(), dao.allMeta().firstOrNull { it.key == MetaKey.PROFILE.key }?.value, Profile()).photo

    private suspend fun readMeta(): BackupMeta {
        val m = dao.allMeta().associate { it.key to it.value }
        val profile = MetaJson.decode(Profile.serializer(), m[MetaKey.PROFILE.key], Profile())
        return BackupMeta(
            profile.copy(photo = null),
            MetaJson.decode(Reading.serializer(), m[MetaKey.READING.key], Reading()),
            MetaJson.decode(PhotoPins.serializer(), m[MetaKey.PINS.key], PhotoPins()),
            hasProfilePhoto = profile.photo != null && store.file("${profile.photo}.jpg").exists(),
            challenge = MetaJson.decode(ChallengeInfo.serializer(), m[MetaKey.CHALLENGE.key], ChallengeInfo()),
            pause = decodePause(m[MetaKey.PAUSE.key]),
            foodGoals = MetaJson.decode(FoodGoals.serializer(), m[MetaKey.FOOD_GOALS.key], FoodGoals()),
        )
    }

    private suspend fun snapshot(app: String): Backup {
        val tasks = dao.allTasks().groupBy { it.runId }
        val logs = dao.allLogs().groupBy { it.date }
        val workouts = dao.allWorkouts().groupBy { it.date }
        val reads = dao.allReads().associateBy { it.date }
        return Backup(
            createdAt = Instant.now().toString(),
            app = app,
            runs = dao.allRuns().map { r ->
                BackupRun(
                    r.id, r.program, r.name, r.startDate, r.lengthDays, r.resetOnMiss, r.attempt, r.active,
                    r.endDate, r.endReason, r.cleared,
                    tasks[r.id].orEmpty().sortedBy { it.position }.map { BackupTask(it.taskId, it.label, it.sub, it.kind, it.outdoor) },
                )
            },
            days = (logs.keys + workouts.keys + reads.keys).sorted().map { d ->
                BackupDay(
                    d,
                    logs[d].orEmpty().associate { it.taskId to it.doneAt },
                    workouts[d].orEmpty().map { BackupWorkout(it.taskId, it.type, it.mins, it.outdoor) },
                    reads[d]?.let { BackupRead(it.title, it.page, it.total, it.pages) },
                )
            },
            meta = readMeta(),
            // A photo whose file has gone missing can't be restored, so leave it out.
            photos = dao.allPhotos()
                .filter { store.file(it.file).exists() }
                .sortedBy { it.date }
                .map { BackupPhoto(it.id, it.date, it.angle, it.source, it.createdAt) },
        )
    }

    /**
     * Replaces everything in ARISE with the backup [open] reads. [open] must give a fresh
     * stream each call (the zip is read twice). Nothing changes unless the whole backup
     * reads cleanly. Returns the restored backup's manifest.
     */
    suspend fun restore(open: () -> InputStream): Backup = withContext(Dispatchers.IO) {
        val manifest = open().use { readEntry(it, BACKUP_MANIFEST) }
            ?: throw BadBackup("This zip has no manifest.json, so it isn't an ARISE backup.")
        val b = BackupJson.decode(manifest)

        // Stage every photo under new file names first, so a failure leaves the current data alone.
        val want = b.photos.associateBy { it.entry }
        val stamp = System.currentTimeMillis()
        val saved = mutableMapOf<String, PhotoStore.Saved>()
        val tmp = File(scratch, "restore-$stamp.jpg")
        var profileSaved: String? = null
        try {
            ZipInputStream(open().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.name == BACKUP_PROFILE_PHOTO && b.meta.hasProfilePhoto) {
                        tmp.outputStream().use { z.copyTo(it) }
                        store.save(tmp, "profile-r$stamp")
                        profileSaved = "profile-r$stamp"
                        continue
                    }
                    val p = want[e.name] ?: continue
                    tmp.outputStream().use { z.copyTo(it) }
                    saved[p.id] = store.save(tmp, "${p.id}-r$stamp")
                }
            }
            val missing = b.photos.count { it.id !in saved }
            if (missing > 0) throw BadBackup("$missing ${if (missing == 1) "photo is" else "photos are"} missing from this backup.")

            val old = dao.allPhotos()
            val oldProfile = profileId()
            db.withTransaction {
                dao.wipeReads()
                dao.wipeMeta()
                dao.wipeLogs()
                dao.wipeWorkouts()
                dao.wipeTasks()
                dao.wipeRuns()
                dao.wipePhotos()
                dao.insertRuns(b.runs.map { r ->
                    RunEntity(r.id, r.program, r.name, r.startDate, r.lengthDays, r.resetOnMiss, r.attempt, r.active, r.endDate, r.endReason, r.cleared)
                })
                dao.insertTasks(b.runs.flatMap { r -> r.tasks.mapIndexed { i, t -> TaskEntity(r.id, t.id, i, t.label, t.sub, t.kind, t.outdoor) } })
                dao.insertLogs(b.days.flatMap { d -> d.done.map { (id, at) -> DayLogEntity(d.date, id, at) } })
                dao.insertWorkouts(b.days.flatMap { d -> d.workouts.map { WorkoutEntity(d.date, it.taskId, it.type, it.mins, it.outdoor) } })
                dao.insertPhotos(b.photos.map { p ->
                    val s = saved.getValue(p.id)
                    PhotoEntity(p.id, p.date, p.angle, s.file, s.thumb, p.source, p.createdAt)
                })
                dao.insertReads(b.days.mapNotNull { d -> d.read?.let { ReadEntity(d.date, it.title, it.page, it.total, it.pages) } })
                val m = b.meta
                dao.upsertMeta(MetaEntity(MetaKey.PROFILE.key, MetaJson.encode(Profile.serializer(), m.profile.copy(photo = profileSaved))))
                dao.upsertMeta(MetaEntity(MetaKey.READING.key, MetaJson.encode(Reading.serializer(), m.reading)))
                dao.upsertMeta(MetaEntity(MetaKey.PINS.key, MetaJson.encode(PhotoPins.serializer(), m.pins)))
                dao.upsertMeta(MetaEntity(MetaKey.CHALLENGE.key, MetaJson.encode(ChallengeInfo.serializer(), m.challenge)))
                m.pause?.let { dao.upsertMeta(MetaEntity(MetaKey.PAUSE.key, encodePause(it))) }
                dao.upsertMeta(MetaEntity(MetaKey.FOOD_GOALS.key, MetaJson.encode(FoodGoals.serializer(), m.foodGoals)))
            }
            profileSaved = null // now owned by the database
            oldProfile?.let { store.delete("$it.jpg", "${it}_t.jpg") }
            saved.clear() // now owned by the database
            old.forEach { store.delete(it.file, it.thumb) }
            b
        } catch (e: Exception) {
            saved.values.forEach { store.delete(it.file, it.thumb) }
            profileSaved?.let { store.delete("$it.jpg", "${it}_t.jpg") }
            throw if (e is BadBackup || e is IOException) e else BadBackup("Couldn't restore this backup (${e.javaClass.simpleName}).")
        } finally {
            tmp.delete()
        }
    }

    /**
     * Adds the progress photos from the 75 Hard app's backup zip (`photos/day-N-<ms>.jpg`),
     * dated by their timestamp in [zone], as front photos. Photos already imported are skipped.
     */
    suspend fun importHard75(open: () -> InputStream, zone: ZoneId): Hard75Import = withContext(Dispatchers.IO) {
        val have = dao.allPhotos().map { it.id }.toMutableSet()
        var added = 0
        var already = 0
        var failed = 0
        val other = mutableListOf<String>()
        val tmp = File(scratch, "import75-${System.currentTimeMillis()}.jpg")
        try {
            ZipInputStream(open().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val p = parseHard75Photo(e.name)
                    if (p == null) {
                        other += e.name
                        continue
                    }
                    if (!have.add(p.id)) {
                        already++
                        continue
                    }
                    tmp.outputStream().use { z.copyTo(it) }
                    val s = try {
                        store.save(tmp, p.id)
                    } catch (ex: Exception) {
                        failed++
                        continue
                    }
                    dao.insertPhoto(
                        PhotoEntity(p.id, p.date(zone).toString(), Angle.FRONT.key, s.file, s.thumb, PhotoSource.IMPORT75.key, p.takenAt.toString()),
                    )
                    added++
                }
            }
        } finally {
            tmp.delete()
        }
        Hard75Import(added, already, failed, other)
    }

    /** The bytes of one entry as text, or null if the zip doesn't have it. */
    private fun readEntry(input: InputStream, name: String): String? {
        ZipInputStream(input.buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == name) return z.readBytes().toString(Charsets.UTF_8)
                e = z.nextEntry
            }
        }
        return null
    }
}
