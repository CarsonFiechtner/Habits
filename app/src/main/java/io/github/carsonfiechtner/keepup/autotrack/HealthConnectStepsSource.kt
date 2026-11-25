package io.github.carsonfiechtner.keepup.autotrack

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectStepsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : StepsSource {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun status(): SourceStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> SourceStatus.UNAVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> SourceStatus.NEEDS_UPDATE
        else -> try {
            if (READ_STEPS in client.permissionController.getGrantedPermissions()) {
                SourceStatus.READY
            } else {
                SourceStatus.NEEDS_PERMISSION
            }
        } catch (_: Exception) {
            SourceStatus.UNAVAILABLE
        }
    }

    override suspend fun permissionsToRequest(): Set<String> {
        val background = try {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (_: Exception) {
            false
        }
        return if (background) setOf(READ_STEPS, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND) else setOf(READ_STEPS)
    }

    override suspend fun dailySteps(from: LocalDate, to: LocalDate): Map<LocalDate, Long> {
        // Grouping by a calendar Period slices by local days; Health Connect deduplicates overlapping
        // sources (phone + watch) using the user's data priority settings.
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        return groups.mapNotNull { group ->
            group.result[StepsRecord.COUNT_TOTAL]?.let { group.startTime.toLocalDate() to it }
        }.toMap()
    }

    companion object {
        val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
    }
}
