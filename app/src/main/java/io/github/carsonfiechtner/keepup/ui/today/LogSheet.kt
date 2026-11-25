package io.github.carsonfiechtner.keepup.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(
    state: LogSheetState,
    onDismiss: () -> Unit,
    onLog: (value: Double, note: String?) -> Unit,
    onToggleDone: () -> Unit,
    onEditEntry: (entryId: Long, value: Double, note: String?) -> Unit,
    onDeleteEntry: (entryId: Long) -> Unit,
    onEditHabit: () -> Unit,
    onDetails: () -> Unit,
) {
    val habit = state.row.habit
    val unit = habit.unit
    val result = state.row.result
    var editing by remember { mutableStateOf<Entry?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text("${habit.icon}  ${habit.name}", style = MaterialTheme.typography.headlineSmall)
            Text(
                HabitText.goalSentence(result.goal, unit, habit.period) + " · " +
                    HabitText.progress(result.value, result.goal, unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            habit.pendingGoal(state.today)?.let { pending ->
                Text(
                    "From ${HabitText.date(pending.effectiveFrom)}: ${HabitText.goalSentence(pending, unit, habit.period)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))

            if (state.canLog) {
                if (unit.isBinary) {
                    Button(onClick = onToggleDone, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.row.isDoneOnDate) "Mark not done" else "Mark done today")
                    }
                } else {
                    LogForm(
                        quickStep = unit.quickAddStep,
                        decimals = unit.decimals,
                        unitLabel = unit.label,
                        showLogZero = result.goal.direction == Direction.AT_MOST,
                        onLog = onLog,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        "${HabitText.date(state.date)} is in the past and can't be changed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                if (state.canLog) "Today's entries" else "Entries on ${HabitText.date(state.date)}",
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.entries.isEmpty()) {
                Text(
                    "Nothing logged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            state.entries.forEach { entry ->
                val editable = state.canModify(entry)
                ListItem(
                    modifier = if (editable && !unit.isBinary) Modifier.clickable { editing = entry } else Modifier,
                    supportingContent = entry.note?.let { { Text(it) } },
                    trailingContent = if (editable) {
                        {
                            IconButton(onClick = { onDeleteEntry(entry.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                            }
                        }
                    } else {
                        null
                    },
                ) {
                    Text("${timeOf(entry)}  ·  ${if (unit.isBinary) "Done" else ValueFormat.withUnit(entry.value, unit)}")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row {
                TextButton(onClick = onDetails) { Text("History & stats") }
                TextButton(onClick = onEditHabit) { Text("Edit habit") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            decimals = unit.decimals,
            unitLabel = unit.label,
            onDismiss = { editing = null },
            onSave = { value, note ->
                onEditEntry(entry.id, value, note)
                editing = null
            },
        )
    }
}

@Composable
private fun LogForm(
    quickStep: Double,
    decimals: Int,
    unitLabel: String,
    showLogZero: Boolean,
    onLog: (Double, String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = ValueFormat.parse(amount)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 4).forEach { multiple ->
            val value = quickStep * multiple
            OutlinedButton(onClick = { onLog(value, null) }) { Text("+" + ValueFormat.number(value, decimals)) }
        }
        if (showLogZero) {
            OutlinedButton(onClick = { onLog(0.0, null) }) { Text("Log 0") }
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = amount,
        onValueChange = { amount = it },
        label = { Text("Amount") },
        suffix = if (unitLabel.isNotBlank()) ({ Text(unitLabel) }) else null,
        singleLine = true,
        isError = amount.isNotBlank() && parsed == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("Note (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            parsed?.let {
                onLog(it, note)
                amount = ""
                note = ""
            }
        },
        enabled = parsed != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add") }
}

@Composable
private fun EditEntryDialog(
    entry: Entry,
    decimals: Int,
    unitLabel: String,
    onDismiss: () -> Unit,
    onSave: (Double, String?) -> Unit,
) {
    var amount by remember { mutableStateOf(ValueFormat.number(entry.value, decimals).replace(",", "")) }
    var note by remember { mutableStateOf(entry.note.orEmpty()) }
    val parsed = ValueFormat.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    suffix = if (unitLabel.isNotBlank()) ({ Text(unitLabel) }) else null,
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let { onSave(it, note) } }, enabled = parsed != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun timeOf(entry: Entry): String = entry.loggedAt.atZone(ZoneId.systemDefault()).format(timeFormat)
