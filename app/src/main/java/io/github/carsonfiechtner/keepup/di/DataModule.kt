package io.github.carsonfiechtner.keepup.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.carsonfiechtner.keepup.data.db.EntryDao
import io.github.carsonfiechtner.keepup.data.db.HabitDao
import io.github.carsonfiechtner.keepup.data.db.KeepUpDatabase
import io.github.carsonfiechtner.keepup.data.db.RestoreDao
import io.github.carsonfiechtner.keepup.data.db.UnitDao
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KeepUpDatabase =
        Room.databaseBuilder(context, KeepUpDatabase::class.java, KeepUpDatabase.NAME)
            .addCallback(KeepUpDatabase.SeedCallback)
            .build()

    @Provides fun provideUnitDao(db: KeepUpDatabase): UnitDao = db.unitDao()

    @Provides fun provideHabitDao(db: KeepUpDatabase): HabitDao = db.habitDao()

    @Provides fun provideEntryDao(db: KeepUpDatabase): EntryDao = db.entryDao()

    @Provides fun provideRestoreDao(db: KeepUpDatabase): RestoreDao = db.restoreDao()

    /** Injected everywhere "today" matters, so tests can control time (design doc §12). */
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemDefaultZoneClock
}

/**
 * Like [Clock.systemDefaultZone], but re-reads the default zone on every call, so "today"
 * follows the phone when it changes time zone while the app is running.
 */
private object SystemDefaultZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
