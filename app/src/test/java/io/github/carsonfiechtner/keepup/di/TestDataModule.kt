package io.github.carsonfiechtner.keepup.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.carsonfiechtner.keepup.data.db.AppGroupDao
import io.github.carsonfiechtner.keepup.data.db.EntryDao
import io.github.carsonfiechtner.keepup.data.db.HabitDao
import io.github.carsonfiechtner.keepup.data.db.KeepUpDatabase
import io.github.carsonfiechtner.keepup.data.db.RestoreDao
import io.github.carsonfiechtner.keepup.data.db.UnitDao
import io.github.carsonfiechtner.keepup.data.db.UsageDao
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Singleton

/** Replaces [DataModule] in tests: in-memory database, throwaway settings, and a fixed clock. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {

    /** Wednesday 2026-11-25, 12:00 UTC. */
    val NOW: Instant = Instant.parse("2026-11-25T12:00:00Z")

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KeepUpDatabase =
        Room.inMemoryDatabaseBuilder(context, KeepUpDatabase::class.java)
            .addCallback(KeepUpDatabase.SeedCallback)
            .build()

    @Provides fun provideUnitDao(db: KeepUpDatabase): UnitDao = db.unitDao()

    @Provides fun provideHabitDao(db: KeepUpDatabase): HabitDao = db.habitDao()

    @Provides fun provideEntryDao(db: KeepUpDatabase): EntryDao = db.entryDao()

    @Provides fun provideRestoreDao(db: KeepUpDatabase): RestoreDao = db.restoreDao()

    @Provides fun provideAppGroupDao(db: KeepUpDatabase): AppGroupDao = db.appGroupDao()

    @Provides fun provideUsageDao(db: KeepUpDatabase): UsageDao = db.usageDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.cacheDir, "test-settings-${UUID.randomUUID()}.preferences_pb")
        }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.fixed(NOW, ZoneId.of("UTC"))
}
