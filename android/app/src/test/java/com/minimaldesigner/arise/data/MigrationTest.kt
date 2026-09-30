package com.minimaldesigner.arise.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Builds a real version-1 database from the committed schema (schemas/…/1.json), the
 * way alpha.1 left it on the phone, then opens it with today's AriseDb. Room runs
 * MIGRATION_1_2 and validates every table against the v2 schema, so a wrong migration
 * fails here instead of on the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    private fun createV1() {
        ctx.deleteDatabase(name)
        val path = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = JSONObject(File("schemas/com.minimaldesigner.arise.data.AriseDb/1.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 1

            db.execSQL(
                "INSERT INTO runs (program, name, startDate, lengthDays, resetOnMiss, attempt, active) " +
                    "VALUES ('hard', 'Alex', '2026-09-01', 75, 1, 1, 1)",
            )
            db.execSQL("INSERT INTO tasks VALUES (1, 'w1', 0, 'Workout · 45 min', 'Any training', 'workout', 0)")
            db.execSQL("INSERT INTO day_logs VALUES ('2026-09-01', 'w1', '2026-09-01T07:00')")
            db.execSQL("INSERT INTO workouts VALUES ('2026-09-01', 'w1', 'Run', 50, 1)")
        }
    }

    @Test
    fun `alpha 1 database upgrades to v3 with the challenge intact`() = runBlocking {
        createV1()
        val room = AriseDb.open(ctx, name)
        try {
            val dao = room.challenge()
            val run = dao.observeActive().first()
            assertEquals("Alex", run?.run?.name)
            assertEquals(listOf("w1"), run?.tasks?.map { it.taskId })
            assertEquals(1, dao.observeLogs().first().size)
            assertEquals(50, dao.observeWorkouts().first().single().mins)

            // The new table works.
            assertEquals(0, dao.observePhotos().first().size)
            dao.insertPhoto(PhotoEntity("p1", "2026-09-01", "front", "p1.jpg", "p1_t.jpg", "camera", "2026-09-01T07:05:00Z"))
            assertEquals("p1", dao.photo("p1")?.id)

            // v3 tables work too.
            dao.upsertRead(ReadEntity("2026-09-01", "Book", 10, 300, 10))
            dao.upsertMeta(MetaEntity("reading", "{}"))
            assertEquals("Book", dao.allReads().single().title)
            assertEquals("reading", dao.allMeta().single().key)
        } finally {
            room.close()
            ctx.deleteDatabase(name)
        }
    }
}
