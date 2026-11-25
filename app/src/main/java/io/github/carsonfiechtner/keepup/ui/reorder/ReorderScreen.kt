package io.github.carsonfiechtner.keepup.ui.reorder

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.model.Habit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReorderViewModel @Inject constructor(private val habits: HabitRepository) : ViewModel() {
    val list = habits.activeHabits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Moves the habit at [from] by [delta] positions (-1 = up) and saves the new order. */
    fun move(from: Int, delta: Int) {
        val current = list.value.toMutableList()
        val to = from + delta
        if (from !in current.indices || to !in current.indices) return
        current.add(to, current.removeAt(from))
        viewModelScope.launch { habits.reorder(current.map(Habit::id)) }
    }
}

/** Sets the order habits appear in on Today, History and the widget (unfinished ones still come first). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderScreen(onBack: () -> Unit, viewModel: ReorderViewModel = hiltViewModel()) {
    val habits by viewModel.list.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reorder habits") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            itemsIndexed(habits, key = { _, h -> h.id }) { index, habit ->
                ListItem(
                    modifier = Modifier.animateItem(),
                    leadingContent = { Text(habit.icon, style = MaterialTheme.typography.titleLarge) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${habit.name} up")
                            }
                            IconButton(onClick = { viewModel.move(index, 1) }, enabled = index < habits.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${habit.name} down")
                            }
                        }
                    },
                ) { Text(habit.name) }
            }
        }
    }
}
