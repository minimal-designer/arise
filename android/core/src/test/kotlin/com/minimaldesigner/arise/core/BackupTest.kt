package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BackupTest {
    private val backup = Backup(
        createdAt = "2026-09-27T09:00:00Z",
        app = "0.1.0+test",
        runs = listOf(
            BackupRun(1, "hard", "Alex", "2026-09-08", 75, true, 2, false, "2026-09-18", "missed", 9),
            BackupRun(
                2, "hard", "Alex", "2026-09-19", 75, true, 3, true,
                tasks = listOf(BackupTask("w1", "Workout · 45 min", "Any", "workout"), BackupTask("ph", "Progress photo", kind = "photo")),
            ),
        ),
        days = listOf(BackupDay("2026-09-19", mapOf("w1" to "2026-09-19T07:00"), listOf(BackupWorkout("w1", "Run", 50, true)))),
        photos = listOf(BackupPhoto("75h-1787886769452", "2026-08-28", "front", "import75", "2026-08-28T03:12:49.452Z")),
    )

    private fun rejects(text: String, message: String) {
        try {
            BackupJson.decode(text)
            fail("accepted: $text")
        } catch (e: BadBackup) {
            assertEquals(message, e.message)
        }
    }

    @Test fun roundTrips() {
        assertEquals(backup, BackupJson.decode(BackupJson.encode(backup)))
        assertEquals("photos/75h-1787886769452.jpg", backup.photos[0].entry)
    }

    @Test fun rejectsOtherFiles() {
        rejects("not json", "This zip's manifest isn't an ARISE backup.")
        rejects("""{"format":"something","createdAt":"x"}""", "This zip isn't an ARISE backup.")
        rejects("""{"format":"arise-backup","version":99,"createdAt":"x"}""", "This backup is from a newer ARISE. Update the app first.")
    }

    @Test fun rejectsBadContents() {
        val badDate = backup.copy(days = listOf(BackupDay("27/09/2026")))
        rejects(BackupJson.encode(badDate), "This backup has a date ARISE can't read.")
        val twoLive = backup.copy(runs = backup.runs.map { it.copy(active = true) })
        rejects(BackupJson.encode(twoLive), "This backup has more than one live challenge.")
        val sneaky = backup.copy(photos = listOf(backup.photos[0].copy(id = "../../databases/arise")))
        rejects(BackupJson.encode(sneaky), "This backup has a photo with an unsafe name.")
    }

    @Test fun carriesAPause() {
        val paused = backup.copy(meta = BackupMeta(pause = Pause("Holiday", "2026-09-30", "2026-10-07")))
        assertEquals(paused, BackupJson.decode(BackupJson.encode(paused)))
        assertNull(BackupJson.decode(BackupJson.encode(backup)).meta.pause)
    }

    @Test fun readsVersionOneBackups() {
        val v1 = """{"format":"arise-backup","version":1,"createdAt":"2026-09-27T09:00:00Z","days":[{"date":"2026-09-19","done":{"w1":"t"}}]}"""
        val b = BackupJson.decode(v1)
        assertEquals(BackupMeta(), b.meta)
        assertNull(b.days.single().read)
    }

    @Test fun ignoresUnknownKeys() {
        val text = BackupJson.encode(backup).replaceFirst("{", """{"future":true,""")
        assertEquals(backup, BackupJson.decode(text))
    }

    @Test fun parsesHard75PhotoNames() {
        val p = parseHard75Photo("photos/day-1-1787886769452.jpg")!!
        assertEquals(1, p.day)
        assertEquals("75h-1787886769452", p.id)
        // 03:12 UTC on 28 Aug is 08:42 in India, the same calendar day.
        assertEquals(LocalDate.of(2026, 8, 28), p.date(ZoneId.of("Asia/Kolkata")))
        assertEquals(4, parseHard75Photo("day-4-1789955448474.JPG")!!.day)
        assertEquals(LocalDate.of(2026, 9, 21), parseHard75Photo("day-4-1789955448474.jpg")!!.date(ZoneId.of("Asia/Kolkata")))
    }

    @Test fun ignoresOtherEntries() {
        listOf("photos/", "data.json", "photos/IMG_0001.jpg", "photos/day-1-123.jpg", "photos/day-x-1787886769452.jpg", "__MACOSX/._day-1")
            .forEach { assertNull(it, parseHard75Photo(it)) }
        assertTrue(parseHard75Photo("75hard-backup/photos/day-12-1788174586986.jpeg") != null)
    }
}
