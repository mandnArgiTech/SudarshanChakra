package com.sudarshanchakra.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sudarshanchakra.mdm.data.MdmAppUsageDao
import com.sudarshanchakra.mdm.data.MdmAppUsageEntity
import com.sudarshanchakra.mdm.data.MdmCallLogDao
import com.sudarshanchakra.mdm.data.MdmCallLogEntity
import com.sudarshanchakra.mdm.data.MdmLocationDao
import com.sudarshanchakra.mdm.data.MdmLocationEntity
import com.sudarshanchakra.mdm.data.MdmScreenTimeDao
import com.sudarshanchakra.mdm.data.MdmScreenTimeEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alerts ADD COLUMN metadata TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS mdm_app_usage_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            date TEXT NOT NULL, package_name TEXT NOT NULL,
            app_label TEXT NOT NULL DEFAULT '', foreground_time_sec INTEGER NOT NULL DEFAULT 0,
            launch_count INTEGER NOT NULL DEFAULT 0, category TEXT NOT NULL DEFAULT '',
            synced INTEGER NOT NULL DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS mdm_call_log_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            phone_number_masked TEXT NOT NULL DEFAULT '', call_type TEXT NOT NULL,
            call_timestamp TEXT NOT NULL, duration_sec INTEGER NOT NULL DEFAULT 0,
            contact_name TEXT NOT NULL DEFAULT '', synced INTEGER NOT NULL DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS mdm_screen_time_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            date TEXT NOT NULL, total_screen_time_sec INTEGER NOT NULL DEFAULT 0,
            unlock_count INTEGER NOT NULL DEFAULT 0, synced INTEGER NOT NULL DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS mdm_location_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            latitude REAL NOT NULL, longitude REAL NOT NULL,
            accuracy_meters REAL, altitude_meters REAL,
            speed_mps REAL, bearing REAL, provider TEXT,
            battery_percent INTEGER,
            recorded_at TEXT NOT NULL, synced INTEGER NOT NULL DEFAULT 0)""",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Remove duplicates so unique indexes can be created (keep lowest id per natural key).
        db.execSQL(
            """DELETE FROM mdm_app_usage_cache WHERE rowid IN (
                SELECT a.rowid FROM mdm_app_usage_cache AS a WHERE EXISTS (
                    SELECT 1 FROM mdm_app_usage_cache AS b
                    WHERE b.date = a.date AND b.package_name = a.package_name AND b.id < a.id
                )
            )""",
        )
        db.execSQL(
            """DELETE FROM mdm_screen_time_cache WHERE rowid IN (
                SELECT a.rowid FROM mdm_screen_time_cache AS a WHERE EXISTS (
                    SELECT 1 FROM mdm_screen_time_cache AS b
                    WHERE b.date = a.date AND b.id < a.id
                )
            )""",
        )
        db.execSQL(
            """DELETE FROM mdm_call_log_cache WHERE rowid IN (
                SELECT a.rowid FROM mdm_call_log_cache AS a WHERE EXISTS (
                    SELECT 1 FROM mdm_call_log_cache AS b
                    WHERE b.call_timestamp = a.call_timestamp
                    AND b.phone_number_masked = a.phone_number_masked
                    AND b.call_type = a.call_type AND b.id < a.id
                )
            )""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_mdm_app_usage_date_pkg " +
                "ON mdm_app_usage_cache(date, package_name)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_mdm_screen_time_date ON mdm_screen_time_cache(date)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_mdm_call_log_natural " +
                "ON mdm_call_log_cache(call_timestamp, phone_number_masked, call_type)",
        )
    }
}

@Database(
    entities = [
        AlertEntity::class,
        MdmAppUsageEntity::class,
        MdmCallLogEntity::class,
        MdmScreenTimeEntity::class,
        MdmLocationEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun mdmAppUsageDao(): MdmAppUsageDao
    abstract fun mdmCallLogDao(): MdmCallLogDao
    abstract fun mdmScreenTimeDao(): MdmScreenTimeDao
    abstract fun mdmLocationDao(): MdmLocationDao
}
