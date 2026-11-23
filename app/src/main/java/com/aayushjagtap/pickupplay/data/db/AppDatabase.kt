package com.aayushjagtap.pickupplay.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** The app's local SQLite database, managed by Room. */
@Database(entities = [SessionEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "pickup_play.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** Process-wide instance. Building it is cheap; the file is opened on first query. */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext, NAME).also { instance = it }
            }

        /**
         * Builds a database with the first-run seed. [name] null gives an in-memory database
         * (used by tests). No destructive migration fallback: user data is never silently wiped.
         */
        fun build(context: Context, name: String?): AppDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, name)
            }
            return builder.addCallback(SeedCallback(context.applicationContext)).build()
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
