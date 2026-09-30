package com.minimaldesigner.arise.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.minimaldesigner.arise.core.BadBackup
import com.minimaldesigner.arise.core.DayRecord
import com.minimaldesigner.arise.core.PhotoPins
import com.minimaldesigner.arise.core.Profile
import com.minimaldesigner.arise.core.ReadLog
import com.minimaldesigner.arise.core.Reading
import com.minimaldesigner.arise.core.ProgramId
import com.minimaldesigner.arise.core.Programs
import com.minimaldesigner.arise.core.Run
import com.minimaldesigner.arise.core.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Plan step 5: export → wipe → restore must give back exactly the same data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AriseDb
    private lateinit var store: PhotoStore
    private lateinit var repo: ChallengeRepository
    private lateinit var backups: Backups

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AriseDb::class.java).allowMainThreadQueries().build()
        store = PhotoStore(ctx)
        store.dir.listFiles()?.forEach { it.delete() }
        repo = ChallengeRepository(db, store)
        backups = Backups(db, store, ctx.cacheDir)
    }

    @After fun tearDown() = db.close()

    private fun jpeg(color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private suspend fun addPhoto(id: String, date: String) {
        val f = File(ctx.cacheDir, "$id.jpg").apply { writeBytes(jpeg(Color.RED)) }
        val s = store.save(f, id)
        db.challenge().insertPhoto(PhotoEntity(id, date, "side", s.file, s.thumb, "camera", "2026-09-20T08:00:00Z"))
    }

    /** Everything a backup must carry, without the photo file names (restore renames them). */
    private suspend fun dump(): List<Any> {
        val d = db.challenge()
        return listOf(
            d.allRuns().sortedBy { it.id },
            d.allTasks().sortedWith(compareBy({ it.runId }, { it.position })),
            d.allLogs().sortedWith(compareBy({ it.date }, { it.taskId })),
            d.allWorkouts().sortedWith(compareBy({ it.date }, { it.taskId })),
            d.allPhotos().sortedBy { it.id }.map { listOf(it.id, it.date, it.angle, it.source, it.createdAt) },
            d.allReads().sortedBy { it.date },
            // The profile photo gets a new file name on restore, so compare the rest.
            d.allMeta().filter { it.key != "profile" }.sortedBy { it.key },
        )
    }

    private suspend fun seed() {
        val run = Run(ProgramId.HARD, "Alex", LocalDate.of(2026, 9, 8), 75, true, Programs.hard.tasks)
        repo.start(run, LocalDate.of(2026, 9, 8))
        repo.restart(run.copy(startDate = LocalDate.of(2026, 9, 19), attempt = 2), LocalDate.of(2026, 9, 19), 9)
        val w = Programs.hard.tasks.first().id
        repo.setDay(LocalDate.of(2026, 9, 19), DayRecord(mapOf(w to "2026-09-19T07:00", "read" to "2026-09-19T21:00"), listOf(Workout(w, "Run", 50, true)), ReadLog("Book", 40, 300, 12)))
        addPhoto("p1", "2026-09-19")
        repo.setReading(Reading("Book", 40, 300, 1))
        repo.setPins(PhotoPins(before = "p1"))
        val f = File(ctx.cacheDir, "me.jpg").apply { writeBytes(jpeg(Color.BLUE)) }
        store.save(f, "profile-1")
        repo.setProfile(Profile(photo = "profile-1", heightCm = 188.0))
    }

    @Test fun `backup, wipe and restore gives back the same data`() = runBlocking {
        seed()
        val before = dump()
        val zip = ByteArrayOutputStream().also { assertEquals(1, backups.export(it, "test")) }.toByteArray()

        db.clearAllTables()
        store.dir.listFiles()?.forEach { it.delete() }

        val b = backups.restore { ByteArrayInputStream(zip) }
        assertEquals("test", b.app)
        assertEquals(before, dump())
        val p = db.challenge().allPhotos().single()
        assertTrue(store.file(p.file).length() > 0)
        assertTrue(store.file(p.thumb).length() > 0)
        val meta = repo.meta.first()
        assertEquals(188.0, meta.profile.heightCm)
        assertEquals(Reading("Book", 40, 300, 1), meta.reading)
        assertEquals("p1", meta.pins.before)
        assertTrue(store.file("${meta.profile.photo}_t.jpg").length() > 0)
        assertEquals(ReadLog("Book", 40, 300, 12), repo.days.first()[LocalDate.of(2026, 9, 19)]?.read)
    }

    @Test fun `restoring over existing data replaces it and cleans up old photo files`() = runBlocking {
        seed()
        val zip = ByteArrayOutputStream().also { backups.export(it, "test") }.toByteArray()
        addPhoto("extra", "2026-09-21")
        val extraFile = db.challenge().photo("extra")!!.file

        backups.restore { ByteArrayInputStream(zip) }
        assertEquals(listOf("p1"), db.challenge().allPhotos().map { it.id })
        assertTrue(!store.file(extraFile).exists())
    }

    @Test fun `a backup with a missing photo changes nothing`() = runBlocking {
        seed()
        val good = ByteArrayOutputStream().also { backups.export(it, "test") }.toByteArray()
        // Same manifest, photo left out.
        val manifest = java.util.zip.ZipInputStream(ByteArrayInputStream(good)).use { z ->
            generateSequence { z.nextEntry }.first { it.name == "manifest.json" }
            z.readBytes()
        }
        val broken = zip("manifest.json" to manifest)
        val before = dump()
        try {
            backups.restore { ByteArrayInputStream(broken) }
            fail("restored a backup with a missing photo")
        } catch (e: BadBackup) {
            assertEquals("1 photo is missing from this backup.", e.message)
        }
        assertEquals(before, dump())
    }

    @Test fun `a zip that isn't a backup is refused`() = runBlocking {
        try {
            backups.restore { ByteArrayInputStream(zip("hello.txt" to "hi".toByteArray())) }
            fail("restored a random zip")
        } catch (e: BadBackup) {
            assertEquals("This zip has no manifest.json, so it isn't an ARISE backup.", e.message)
        }
    }

    @Test fun `75 Hard photos import by timestamp, once`() = runBlocking {
        val z = zip(
            "photos/day-1-1787886769452.jpg" to jpeg(Color.BLUE),
            "photos/day-4-1789955448474.jpg" to jpeg(Color.GREEN),
            "photos/day-5-1790000000000.jpg" to "not a photo".toByteArray(),
            "data.json" to "{}".toByteArray(),
        )
        val india = ZoneId.of("Asia/Kolkata")
        val first = backups.importHard75({ ByteArrayInputStream(z) }, india)
        assertEquals(Hard75Import(added = 2, already = 0, failed = 1, other = listOf("data.json")), first)
        val photos = db.challenge().allPhotos().sortedBy { it.date }
        assertEquals(listOf("2026-08-28", "2026-09-21"), photos.map { it.date })
        assertEquals(listOf("front", "front"), photos.map { it.angle })
        assertEquals(listOf("import75", "import75"), photos.map { it.source })

        val again = backups.importHard75({ ByteArrayInputStream(z) }, india)
        assertEquals(0, again.added)
        assertEquals(2, again.already)
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
    }.toByteArray()
}
