package io.github.carsonfiechtner.keepup.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitRow(
    row: HabitRowState,
    onClick: () -> Unit,
    onAction: () -> Unit,
    onEdit: () -> Unit,
    onDetails: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val habit = row.habit
    val color = Color(habit.color)
    val status = row.result.status

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .combinedClickable(
                    onClick = onClick,
                    onClickLabel = "Open log",
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = "More options",
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { row.result.fill },
                    modifier = Modifier.size(44.dp),
                    color = if (status == PeriodStatus.MISSED) MaterialTheme.colorScheme.error else color,
                    trackColor = color.copy(alpha = 0.18f),
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(habit.icon, style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == PeriodStatus.MISSED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.streak > 0) {
                Text(
                    "🔥${row.streak}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RowActionButton(row, onAction)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Details") }, onClick = { menuOpen = false; onDetails() })
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
            DropdownMenuItem(text = { Text("Archive") }, onClick = { menuOpen = false; onArchive() })
        }
    }
}

@Composable
private fun RowActionButton(row: HabitRowState, onAction: () -> Unit) {
    when (row.action) {
        RowAction.TOGGLE -> FilledIconToggleButton(checked = row.isDoneOnDate, onCheckedChange = { onAction() }) {
            Icon(Icons.Filled.Check, contentDescription = if (row.isDoneOnDate) "Mark not done" else "Mark done")
        }
        RowAction.QUICK_ADD -> FilledTonalButton(
            onClick = onAction,
            modifier = Modifier.semantics {
                contentDescription = "Add ${ValueFormat.withUnit(row.habit.unit.quickAddStep, row.habit.unit)} to ${row.habit.name}"
            },
        ) {
            Text("+" + ValueFormat.number(row.habit.unit.quickAddStep, row.habit.unit.decimals))
        }
        RowAction.LOG_ZERO -> OutlinedButton(
            onClick = onAction,
            modifier = Modifier.semantics { contentDescription = "Log zero for ${row.habit.name} today" },
        ) { Text("Log 0") }
        RowAction.NONE -> if (row.result.status == PeriodStatus.COMPLETED) {
            Icon(Icons.Filled.Check, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun subtitle(row: HabitRowState): String {
    val habit = row.habit
    val result = row.result
    val goal = result.goal
    val core = when {
        habit.unit.isBinary && habit.period == Period.DAILY -> when (result.status) {
            PeriodStatus.COMPLETED -> "Done"
            PeriodStatus.MISSED -> "Missed"
            else -> "Not done yet"
        }
        else -> HabitText.progress(result.value, goal, habit.unit)
    }
    val prefix = when (row.daysLeft) {
        null -> ""
        0L -> "Last day · "
        1L -> "1 day left · "
        else -> "${row.daysLeft} days left · "
    }
    val note = when {
        goal.direction == Direction.AT_MOST && result.status == PeriodStatus.MISSED -> " · over limit"
        goal.direction == Direction.AT_MOST && !result.hasData && row.action == RowAction.LOG_ZERO -> " · log today"
        else -> ""
    }
    val auto = if (habit.source.isAutomatic) "Auto · " else ""
    return auto + prefix + core + note
}
