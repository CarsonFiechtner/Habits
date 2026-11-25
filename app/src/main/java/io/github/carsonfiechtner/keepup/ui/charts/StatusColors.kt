package io.github.carsonfiechtner.keepup.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus

/** Fill color for a period of a habit drawn in [habitColor]. */
@Composable
fun statusColor(status: PeriodStatus, habitColor: Color): Color = when (status) {
    PeriodStatus.COMPLETED -> habitColor
    PeriodStatus.MISSED -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
    PeriodStatus.IN_PROGRESS -> habitColor.copy(alpha = 0.4f)
    PeriodStatus.NOT_SCHEDULED -> MaterialTheme.colorScheme.surfaceVariant
}

fun statusLabel(status: PeriodStatus): String = when (status) {
    PeriodStatus.COMPLETED -> "completed"
    PeriodStatus.MISSED -> "missed"
    PeriodStatus.IN_PROGRESS -> "in progress"
    PeriodStatus.NOT_SCHEDULED -> "not started"
}
