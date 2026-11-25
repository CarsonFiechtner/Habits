package io.github.carsonfiechtner.keepup.ui.appgroups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carsonfiechtner.keepup.autotrack.InstalledApp
import io.github.carsonfiechtner.keepup.autotrack.UsageSource
import io.github.carsonfiechtner.keepup.data.repo.AppGroupRepository
import io.github.carsonfiechtner.keepup.domain.model.AppGroup
import io.github.carsonfiechtner.keepup.ui.components.EmptyState
import io.github.carsonfiechtner.keepup.ui.navigation.AppGroupEditRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ---- List ----

@HiltViewModel
class AppGroupsViewModel @Inject constructor(repo: AppGroupRepository) : ViewModel() {
    val groups = repo.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGroupsScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: AppGroupsViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App groups") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(0) }) { Icon(Icons.Filled.Add, contentDescription = "New app group") }
        },
    ) { padding ->
        val list = groups ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "No app groups yet",
                body = "Group apps like Instagram and Reddit as \"Social\" to track the time you spend in them.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(contentPadding = padding) {
            items(list, key = { it.id }) { group ->
                ListItem(
                    modifier = Modifier.clickable { onEdit(group.id) },
                    supportingContent = { Text("${group.packages.size} apps") },
                ) { Text(group.name) }
            }
        }
    }
}

// ---- Edit ----

data class AppGroupEditState(
    val loading: Boolean = true,
    val id: Long? = null,
    val name: String = "",
    val selected: Set<String> = emptySet(),
    val apps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val finished: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && selected.isNotEmpty()

    /** Selected apps first, then by name; filtered by the search box. */
    val visibleApps: List<InstalledApp>
        get() = apps
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ it.packageName !in selected }, { it.label.lowercase() }))
}

@HiltViewModel
class AppGroupEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: AppGroupRepository,
    private val usage: UsageSource,
) : ViewModel() {
    private val groupId = savedStateHandle.toRoute<AppGroupEditRoute>().groupId.takeIf { it > 0 }
    private val _state = MutableStateFlow(AppGroupEditState())
    val state: StateFlow<AppGroupEditState> = _state.asStateFlow()
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { usage.launchableApps() }
            val group: AppGroup? = groupId?.let { repo.get(it) }
            // Keep members that were uninstalled, so saving doesn't silently drop them.
            val missing = group?.packages.orEmpty().filter { pkg -> apps.none { it.packageName == pkg } }
                .map { InstalledApp(it, "$it (not installed)") }
            _state.value = AppGroupEditState(
                loading = false,
                id = group?.id,
                name = group?.name.orEmpty(),
                selected = group?.packages.orEmpty(),
                apps = apps + missing,
            )
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun setQuery(value: String) = _state.update { it.copy(query = value) }
    fun toggle(pkg: String) = _state.update { s ->
        s.copy(selected = if (pkg in s.selected) s.selected - pkg else s.selected + pkg)
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            try {
                repo.save(s.id, s.name, s.selected)
                _state.update { it.copy(finished = true) }
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            if (repo.delete(id)) {
                _state.update { it.copy(finished = true) }
            } else {
                messageChannel.send("A habit tracks this group. Archive or delete that habit first.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGroupEditScreen(onDone: () -> Unit, viewModel: AppGroupEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == null) "New app group" else "Edit app group") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.id != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete group") }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave) { Text("Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Name (e.g. Social)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${state.selected.size} selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            LazyColumn {
                items(state.visibleApps, key = { it.packageName }) { app ->
                    ListItem(
                        modifier = Modifier.clickable { viewModel.toggle(app.packageName) },
                        leadingContent = { Checkbox(checked = app.packageName in state.selected, onCheckedChange = null) },
                        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                    ) { Text(app.label) }
                }
            }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${state.name}?") },
            text = { Text("Saved usage data is kept; only the group is removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
