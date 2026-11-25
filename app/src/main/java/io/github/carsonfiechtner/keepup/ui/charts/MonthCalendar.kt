package io.github.carsonfiechtner.keepup.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** A month grid; each day is colored by [statusOf] (null = no habit that day). */
@Composable
fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    habitColor: Color,
    statusOf: (LocalDate) -> PeriodStatus?,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val weeks = cells.chunked(7).map { it + List(7 - it.size) { null } }

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                Text(
                    firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) DayCell(date, today, habitColor, statusOf(date))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, habitColor: Color, status: PeriodStatus?) {
    val background = status?.let { statusColor(it, habitColor) } ?: Color.Transparent
    val textColor = when (status) {
        PeriodStatus.COMPLETED -> Color.White
        null, PeriodStatus.NOT_SCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(background, CircleShape)
            .then(
                if (date == today) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
            )
            .semantics { contentDescription = "$date ${status?.let(::statusLabel) ?: ""}" },
        contentAlignment = Alignment.Center,
    ) {
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}
