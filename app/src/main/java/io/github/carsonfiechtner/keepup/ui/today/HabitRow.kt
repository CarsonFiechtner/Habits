package io.github.carsonfiechtner.keepup.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat
import io.github.carsonfiechtner.keepup.ui.stage.StreakBadge
import io.github.carsonfiechtner.keepup.ui.stage.stageColor

/** A habit card: icon tile, progress, streak badge, one-tap action and a progress bar along the bottom. */
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
    val color = stageColor(row.stage)
    val missed = row.result.status == PeriodStatus.MISSED

    Box(modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onClickLabel = "Open log",
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = "More options",
                ),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(color.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(habit.icon, style = MaterialTheme.typography.titleMedium) }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                habit.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (habit.source.isAutomatic) AutoTag()
                        }
                        Text(
                            subtitle(row),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (row.streak > 0) StreakBadge(row.stage, row.streak, habit.period)
                    RowActionButton(row, onAction)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(row.result.fill)
                            .height(4.dp)
                            .background(if (missed) MaterialTheme.colorScheme.error else color),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Details") }, onClick = { menuOpen = false; onDetails() })
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
            DropdownMenuItem(text = { Text("Archive") }, onClick = { menuOpen = false; onArchive() })
        }
    }
}

@Composable
fun AutoTag() {
    Text(
        "AUTO",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
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
    val suffix = when (row.daysLeft) {
        null -> ""
        0L -> " · last day"
        1L -> " · last day tomorrow"
        else -> " · ${row.daysLeft} days left"
    }
    val note = when {
        goal.direction == Direction.AT_MOST && result.status == PeriodStatus.MISSED -> " · over limit"
        goal.direction == Direction.AT_MOST && !result.hasData && row.action == RowAction.LOG_ZERO -> " · log today"
        else -> ""
    }
    return core + suffix + note
}
