package io.github.carsonfiechtner.keepup.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat

/** Values entered in [UnitDialog]. */
data class UnitInput(val name: String, val label: String, val decimals: Int, val quickAdd: Double)

/**
 * Create or edit a unit. For built-in units only the quick-add step can change, since their
 * name and precision are shared assumptions across habits.
 */
@Composable
fun UnitDialog(
    existing: MeasureUnit?,
    onDismiss: () -> Unit,
    onConfirm: (UnitInput) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val builtin = existing?.isBuiltin == true
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var decimals by remember { mutableIntStateOf(existing?.decimals ?: 0) }
    var step by remember {
        mutableStateOf(existing?.let { ValueFormat.number(it.quickAddStep, it.decimals).replace(",", "") } ?: "1")
    }
    val parsedStep = ValueFormat.parse(step)?.takeIf { it > 0 }
    val valid = name.isNotBlank() && parsedStep != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New unit" else "Edit unit") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Glasses)") },
                    singleLine = true,
                    enabled = !builtin,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Short label (e.g. glasses)") },
                    singleLine = true,
                    enabled = !builtin,
                )
                Spacer(Modifier.height(8.dp))
                Text("Decimal places", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow {
                    (0..MeasureUnit.MAX_DECIMALS).forEach { n ->
                        SegmentedButton(
                            selected = decimals == n,
                            onClick = { decimals = n },
                            shape = SegmentedButtonDefaults.itemShape(n, MeasureUnit.MAX_DECIMALS + 1),
                            enabled = !builtin,
                        ) { Text("$n") }
                    }
                }
                OutlinedTextField(
                    value = step,
                    onValueChange = { step = it },
                    label = { Text("Quick-add amount") },
                    singleLine = true,
                    isError = parsedStep == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete unit", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(UnitInput(name.trim(), label.trim(), decimals, parsedStep!!)) },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Deleting wipes history, so it requires typing the habit's name (design doc §6.5). */
@Composable
fun DeleteHabitDialog(habitName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $habitName?") },
        text = {
            Column {
                Text("This permanently deletes the habit and all of its history. Archiving keeps the history instead.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type \"$habitName\" to confirm") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == habitName.trim()) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
