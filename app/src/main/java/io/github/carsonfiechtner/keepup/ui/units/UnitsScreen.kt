package io.github.carsonfiechtner.keepup.ui.units

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.ui.common.UnitDialog
import io.github.carsonfiechtner.keepup.ui.common.UnitInput
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnitsViewModel @Inject constructor(private val habits: HabitRepository) : ViewModel() {
    val units = habits.units.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun create(input: UnitInput) {
        viewModelScope.launch { habits.createUnit(input.name, input.label, input.decimals, input.quickAdd) }
    }

    fun update(unit: MeasureUnit, input: UnitInput) {
        viewModelScope.launch {
            habits.updateUnit(
                if (unit.isBuiltin) {
                    unit.copy(quickAddStep = input.quickAdd)
                } else {
                    unit.copy(name = input.name, label = input.label, decimals = input.decimals, quickAddStep = input.quickAdd)
                },
            )
        }
    }

    fun delete(unit: MeasureUnit) {
        viewModelScope.launch {
            if (!habits.deleteUnit(unit.id)) messageChannel.send("${unit.name} is used by a habit and can't be deleted")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsScreen(onBack: () -> Unit, viewModel: UnitsViewModel = hiltViewModel()) {
    val units by viewModel.units.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MeasureUnit?>(null) }
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Units") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "New unit") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(units.filter { !it.isBinary }, key = { it.id }) { unit ->
                ListItem(
                    modifier = Modifier.clickable { editing = unit },
                    supportingContent = {
                        Text(
                            buildString {
                                if (unit.label.isNotBlank()) append("${unit.label} · ")
                                append("${unit.decimals} decimals · quick add +")
                                append(ValueFormat.number(unit.quickAddStep, unit.decimals))
                                if (unit.isBuiltin) append(" · built-in")
                            },
                        )
                    },
                ) { Text(unit.name) }
            }
        }
    }

    editing?.let { unit ->
        UnitDialog(
            existing = unit,
            onDismiss = { editing = null },
            onConfirm = {
                viewModel.update(unit, it)
                editing = null
            },
            onDelete = if (unit.isBuiltin) {
                null
            } else {
                {
                    viewModel.delete(unit)
                    editing = null
                }
            },
        )
    }
    if (creating) {
        UnitDialog(
            existing = null,
            onDismiss = { creating = false },
            onConfirm = {
                viewModel.create(it)
                creating = false
            },
        )
    }
}

