package com.aayushjagtap.pickupplay.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The app's local SQLite database, managed by Room. */
@Database(entities = [SessionEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "pickup_play.db"

        /**
         * Version 1 -> 2 (A4 Phase 2): adds cloud-sync columns without touching existing rows.
         * Sessions the user already created get a fresh UUID remote id and are queued for upload;
         * sample sessions stay local. Nothing is deleted or re-seeded.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `remote_id` TEXT")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `owner_uid` TEXT")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `sync_state` TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `synced_at` INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sessions_remote_id` ON `sessions` (`remote_id`)"
                )
                db.execSQL(
                    "UPDATE `sessions` SET `remote_id` = $SQL_UUID_V4, `sync_state` = 'PENDING' " +
                        "WHERE `is_hosting` = 1"
                )
            }
        }

        /** Random version-4 UUID text, evaluated per row by SQLite. */
        private const val SQL_UUID_V4 =
            "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
                "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))), 2) || " +
                "'-' || lower(hex(randomblob(6))))"

        @Volatile
        private var instance: AppDatabase? = null

        /** Process-wide instance. Building it is cheap; the file is opened on first query. */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext, NAME).also { instance = it }
            }

        /**
         * Builds a database with the first-run seed. [name] null gives an in-memory database
         * (used by tests). Schema changes go through explicit migrations; there is no destructive
         * fallback, so user data is never silently wiped.
         */
        fun build(context: Context, name: String?): AppDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, name)
            }
            return builder
                .addMigrations(MIGRATION_1_2)
                .addCallback(SeedCallback(context.applicationContext))
                .build()
        }
    }

    /**
     * Inserts the sample sessions exactly once: Room calls [onCreate] only when the database
     * file is first created, inside the same transaction that creates the tables. Later launches
     * open the existing file and never re-seed, so samples are not duplicated or reset.
     */
    private class SeedCallback(private val context: Context) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            SeedSessions.entities(context).forEach { row ->
                db.insert(SessionTable.NAME, SQLiteDatabase.CONFLICT_IGNORE, row.toContentValues())
            }
        }
    }
}
