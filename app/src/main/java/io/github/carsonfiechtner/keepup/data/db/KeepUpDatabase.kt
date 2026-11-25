package io.github.carsonfiechtner.keepup.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.carsonfiechtner.keepup.domain.model.UnitKind

@Database(
    entities = [
        UnitEntity::class,
        HabitEntity::class,
        GoalEntity::class,
        EntryEntity::class,
        AppGroupEntity::class,
        AppGroupMemberEntity::class,
        UsageDailyEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2: app groups and saved daily usage (M5). New tables only.
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class KeepUpDatabase : RoomDatabase() {
    abstract fun unitDao(): UnitDao
    abstract fun habitDao(): HabitDao
    abstract fun entryDao(): EntryDao
    abstract fun restoreDao(): RestoreDao
    abstract fun appGroupDao(): AppGroupDao
    abstract fun usageDao(): UsageDao

    /** Seeds the built-in units (design doc U1) when the database is first created. */
    object SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            BUILTIN_UNITS.forEach { u ->
                db.execSQL(
                    "INSERT INTO unit (name, label, kind, decimals, quick_add, is_builtin) VALUES (?, ?, ?, ?, ?, 1)",
                    arrayOf<Any>(u.name, u.label, u.kind.name, u.decimals, u.quickAdd),
                )
            }
        }
    }

    companion object {
        const val NAME = "keepup.db"

        val BUILTIN_UNITS: List<UnitEntity> = listOf(
            UnitEntity(
                name = "Done",
                label = "",
                kind = UnitKind.BINARY,
                decimals = 0,
                quickAdd = 1.0,
                isBuiltin = true,
            ),
            builtin("Times", "times", 0, 1.0),
            builtin("Minutes", "min", 0, 5.0),
            builtin("Hours", "h", 1, 0.5),
            builtin("Steps", "steps", 0, 1000.0),
            builtin("Liters", "L", 2, 0.25),
            builtin("Milliliters", "ml", 0, 250.0),
            builtin("Pages", "pages", 0, 5.0),
            builtin("Kilometers", "km", 1, 1.0),
        )

        private fun builtin(name: String, label: String, decimals: Int, quickAdd: Double) = UnitEntity(
            name = name,
            label = label,
            kind = UnitKind.NUMBER,
            decimals = decimals,
            quickAdd = quickAdd,
            isBuiltin = true,
        )
    }
}
