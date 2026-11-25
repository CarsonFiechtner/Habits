package io.github.carsonfiechtner.keepup.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.carsonfiechtner.keepup.autotrack.HealthConnectStepsSource
import io.github.carsonfiechtner.keepup.autotrack.StepsSource

/** Real data sources; tests replace this module with fakes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AutoTrackModule {
    @Binds
    abstract fun bindStepsSource(impl: HealthConnectStepsSource): StepsSource
}
