package io.github.carsonfiechtner.keepup.ui.archived

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domainops.HabitActions
import io.github.carsonfiechtner.keepup.ui.common.DeleteHabitDialog
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.components.EmptyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ArchivedViewModel @Inject constructor(
    habits: HabitRepository,
    private val actions: HabitActions,
) : ViewModel() {
    val archived = habits.archivedHabits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun unarchive(habit: Habit) {
        viewModelScope.launch { actions.unarchive(habit.id) }
    }

    fun delete(habit: Habit) {
        viewModelScope.launch { actions.delete(habit.id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(onBack: () -> Unit, onOpenHabit: (Long) -> Unit, viewModel: ArchivedViewModel = hiltViewModel()) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Habit?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archived habits") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val list = archived ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Info,
                title = "Nothing archived",
                body = "Archived habits keep their history and can be restored here.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(contentPadding = padding) {
            items(list, key = { it.id }) { habit ->
                ListItem(
                    modifier = Modifier.clickable { onOpenHabit(habit.id) },
                    leadingContent = { Text(habit.icon, style = MaterialTheme.typography.titleLarge) },
                    supportingContent = {
                        habit.archivedAt?.let {
                            Text("Archived ${HabitText.date(it.atZone(ZoneId.systemDefault()).toLocalDate())}")
                        }
                    },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { viewModel.unarchive(habit) }) { Text("Restore") }
                            TextButton(onClick = { deleting = habit }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                ) { Text(habit.name) }
            }
        }
    }

    deleting?.let { habit ->
        DeleteHabitDialog(
            habitName = habit.name,
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.delete(habit)
                deleting = null
            },
        )
    }
}
