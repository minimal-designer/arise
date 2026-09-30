package com.minimaldesigner.arise.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// Dates are ISO strings (yyyy-MM-dd): sortable, readable in a backup, no converters.

/** A challenge. Exactly one row has active = 1 while a challenge runs; ended or restarted attempts stay as history. */
@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
)

@Entity(tableName = "tasks", primaryKeys = ["runId", "taskId"])
data class TaskEntity(
    val runId: Long,
    val taskId: String,
    val position: Int,
    val label: String,
    val sub: String,
    val kind: String,
    val outdoor: Boolean,
)

/** A ticked task on a calendar day. */
@Entity(tableName = "day_logs", primaryKeys = ["date", "taskId"])
data class DayLogEntity(
    val date: String,
    val taskId: String,
    val doneAt: String,
)

@Entity(tableName = "workouts", primaryKeys = ["date", "taskId"])
data class WorkoutEntity(
    val date: String,
    val taskId: String,
    val type: String,
    val mins: Int,
    val outdoor: Boolean,
)

/** A progress photo. [file] and [thumb] are names inside filesDir/photos. */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val date: String,
    val angle: String,
    val file: String,
    val thumb: String,
    val source: String,
    val createdAt: String,
)

/** What the Read task logged on a day (v3). */
@Entity(tableName = "reads")
data class ReadEntity(
    @PrimaryKey val date: String,
    val title: String,
    val page: Int,
    val total: Int,
    val pages: Int,
)

/** Small JSON values keyed by name: profile, reading, pins (v3). See :core MetaKey. */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class RunWithTasks(
    @Embedded val run: RunEntity,
    @Relation(parentColumn = "id", entityColumn = "runId") val tasks: List<TaskEntity>,
)

@Dao
interface ChallengeDao {
    @Transaction
    @Query("SELECT * FROM runs WHERE active = 1 ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<RunWithTasks?>

    @Query("SELECT * FROM day_logs")
    fun observeLogs(): Flow<List<DayLogEntity>>

    @Query("SELECT * FROM workouts")
    fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM runs WHERE active = 1 ORDER BY id DESC LIMIT 1")
    suspend fun active(): RunEntity?

    /** Ended and restarted attempts, newest first. */
    @Query("SELECT * FROM runs WHERE active = 0 ORDER BY id DESC")
    fun observeHistory(): Flow<List<RunEntity>>

    @Query("UPDATE runs SET name = :name WHERE active = 1")
    suspend fun rename(name: String)

    @Insert
    suspend fun insertRun(run: RunEntity): Long

    @Upsert
    suspend fun upsertRun(run: RunEntity)

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("UPDATE runs SET active = 0 WHERE active = 1")
    suspend fun deactivateAll()

    @Query("DELETE FROM day_logs WHERE date = :date")
    suspend fun clearLogs(date: String)

    @Query("DELETE FROM workouts WHERE date = :date")
    suspend fun clearWorkouts(date: String)

    @Insert
    suspend fun insertLogs(logs: List<DayLogEntity>)

    @Insert
    suspend fun insertWorkouts(workouts: List<WorkoutEntity>)

    @Query("SELECT * FROM photos")
    fun observePhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun photo(id: String): PhotoEntity?

    @Insert
    suspend fun insertPhoto(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: String)

    @Query("UPDATE photos SET angle = :angle WHERE id = :id")
    suspend fun setPhotoAngle(id: String, angle: String)

    // ---- backup and restore: every row of every table ----

    @Query("SELECT * FROM runs")
    suspend fun allRuns(): List<RunEntity>

    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<TaskEntity>

    @Query("SELECT * FROM day_logs")
    suspend fun allLogs(): List<DayLogEntity>

    @Query("SELECT * FROM workouts")
    suspend fun allWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM photos")
    suspend fun allPhotos(): List<PhotoEntity>

    @Insert
    suspend fun insertRuns(runs: List<RunEntity>)

    @Insert
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM runs")
    suspend fun wipeRuns()

    @Query("DELETE FROM tasks")
    suspend fun wipeTasks()

    @Query("DELETE FROM day_logs")
    suspend fun wipeLogs()

    @Query("DELETE FROM workouts")
    suspend fun wipeWorkouts()

    @Query("DELETE FROM photos")
    suspend fun wipePhotos()

    // ---- v3: reading and meta ----

    @Query("SELECT * FROM reads")
    fun observeReads(): Flow<List<ReadEntity>>

    @Query("SELECT * FROM reads")
    suspend fun allReads(): List<ReadEntity>

    @Upsert
    suspend fun upsertRead(read: ReadEntity)

    @Insert
    suspend fun insertReads(reads: List<ReadEntity>)

    @Query("DELETE FROM reads WHERE date = :date")
    suspend fun clearRead(date: String)

    @Query("DELETE FROM reads")
    suspend fun wipeReads()

    @Query("SELECT * FROM meta")
    fun observeMeta(): Flow<List<MetaEntity>>

    @Query("SELECT * FROM meta")
    suspend fun allMeta(): List<MetaEntity>

    @Upsert
    suspend fun upsertMeta(meta: MetaEntity)

    @Query("DELETE FROM meta")
    suspend fun wipeMeta()

    @Query("DELETE FROM meta WHERE `key` = :key")
    suspend fun deleteMeta(key: String)
}

/** v2 adds progress photos. Must match Room's generated `photos` table exactly. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `photos` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `angle` TEXT NOT NULL, " +
                "`file` TEXT NOT NULL, `thumb` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/** v3 adds the Read task's log and the meta values. Must match Room's generated tables exactly. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reads` (`date` TEXT NOT NULL, `title` TEXT NOT NULL, `page` INTEGER NOT NULL, " +
                "`total` INTEGER NOT NULL, `pages` INTEGER NOT NULL, PRIMARY KEY(`date`))",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
    }
}

@Database(
    entities = [RunEntity::class, TaskEntity::class, DayLogEntity::class, WorkoutEntity::class, PhotoEntity::class, ReadEntity::class, MetaEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AriseDb : RoomDatabase() {
    abstract fun challenge(): ChallengeDao

    companion object {
        const val NAME = "arise.db"

        fun open(context: Context, name: String = NAME): AriseDb =
            Room.databaseBuilder(context, AriseDb::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
