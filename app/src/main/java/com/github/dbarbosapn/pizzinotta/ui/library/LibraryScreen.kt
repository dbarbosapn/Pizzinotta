package com.github.dbarbosapn.pizzinotta.ui.library

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.dbarbosapn.pizzinotta.R
import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.import.ImportResult
import com.github.dbarbosapn.pizzinotta.data.import.UriImportSource
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToPlaylists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val localContext = LocalContext.current

    @SuppressLint("LocalContextResourcesRead")
    fun resString(id: Int, vararg args: Any): String = localContext.resources.getString(id, *args)

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val sources = uris.map { UriImportSource(it, localContext.contentResolver) }
        scope.launch {
            var imported = 0
            var skipped = 0
            var failed = 0
            viewModel.importSources(sources).collect { result ->
                when (result) {
                    is ImportResult.Imported -> imported++
                    is ImportResult.Duplicate -> skipped++
                    is ImportResult.Failure -> failed++
                }
            }
            snackbarHostState.showSnackbar(
                resString(R.string.import_summary, imported, skipped, failed)
            )
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> importUris(uris) }

    var renameTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTarget by remember { mutableStateOf<Song?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            when {
                ui.multiSelecting -> MultiSelectTopBar(
                    count = ui.selection.size,
                    onClose = viewModel::clearSelection,
                    onAddToPlaylist = {
                        scope.launch { snackbarHostState.showSnackbar(resString(R.string.coming_soon)) }
                    },
                    onDelete = { showDeleteConfirm = true },
                )
                ui.searching -> SearchTopBar(
                    query = ui.searchQuery,
                    onQueryChange = viewModel::setQuery,
                    onExit = viewModel::exitSearch,
                )
                else -> LibraryTopBar(
                    onNavigateToPlaylists = onNavigateToPlaylists,
                    onEnterSearch = viewModel::enterSearch,
                    onSelectOrder = viewModel::setOrder,
                    currentOrder = ui.order,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickerLauncher.launch("application/pdf") }) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.import_import))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                ui.searching && ui.songs.isEmpty() -> NoResults(ui.searchQuery)
                ui.isEmpty -> EmptyState(onImport = { pickerLauncher.launch("application/pdf") })
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = ui.songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            selected = song.id in ui.selection,
                            multiSelecting = ui.multiSelecting,
                            onClick = {
                                if (ui.multiSelecting) {
                                    viewModel.toggleSelection(song.id)
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(resString(R.string.coming_soon))
                                    }
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(song.id) },
                            onRename = { target -> renameTarget = target },
                            onDelete = { deleteTarget = it },
                            onAddToPlaylist = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(resString(R.string.coming_soon))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { song ->
        RenameDialog(
            initial = song.displayTitle,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                scope.launch {
                    viewModel.rename(song, newTitle)
                    renameTarget = null
                }
            },
        )
    }

    deleteTarget?.let { song ->
        DeleteConfirmDialog(
            count = 1,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    viewModel.deleteSong(song)
                    deleteTarget = null
                    snackbarHostState.showSnackbar(resString(R.string.deleted_count, 1))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            count = ui.selection.size,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                scope.launch {
                    val deleted = viewModel.deleteSelected()
                    showDeleteConfirm = false
                    snackbarHostState.showSnackbar(resString(R.string.deleted_count, deleted))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    onNavigateToPlaylists: () -> Unit,
    onEnterSearch: () -> Unit,
    onSelectOrder: (SongOrder) -> Unit,
    currentOrder: SongOrder,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.library)) },
        actions = {
            IconButtonNavigation(onClick = onNavigateToPlaylists, painter = R.drawable.ic_playlist, desc = R.string.playlists)
            IconButtonNavigation(onClick = onEnterSearch, painter = R.drawable.ic_search, desc = R.string.search_hint)
            SortMenu(current = currentOrder, onSelect = onSelectOrder)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onExit: () -> Unit,
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                trailingIcon = {
                    IconButtonNavigation(onClick = onExit, painter = R.drawable.ic_arrow_back, desc = R.string.cancel)
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectTopBar(
    count: Int,
    onClose: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, count)) },
        navigationIcon = {
            IconButtonNavigation(onClick = onClose, painter = R.drawable.ic_arrow_back, desc = R.string.cancel)
        },
        actions = {
            IconButtonNavigation(onClick = onAddToPlaylist, painter = R.drawable.ic_add, desc = R.string.add_to_playlist)
            IconButtonNavigation(onClick = onDelete, painter = R.drawable.ic_delete, desc = R.string.delete)
        },
    )
}

@Composable
private fun SortMenu(current: SongOrder, onSelect: (SongOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButtonNavigation(
        onClick = { expanded = true },
        painter = R.drawable.ic_sort,
        desc = R.string.sort_by,
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_name)) },
            leadingIcon = {
                if (current == SongOrder.TITLE) {
                    Icon(painterResource(R.drawable.ic_check), null)
                }
            },
            onClick = { onSelect(SongOrder.TITLE); expanded = false },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_recently_opened)) },
            leadingIcon = {
                if (current == SongOrder.RECENTLY_OPENED) {
                    Icon(painterResource(R.drawable.ic_check), null)
                }
            },
            onClick = { onSelect(SongOrder.RECENTLY_OPENED); expanded = false },
        )
    }
}

@Composable
private fun SongRow(
    song: Song,
    selected: Boolean,
    multiSelecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: (Song) -> Unit,
    onDelete: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .pointerInput(multiSelecting) { detectTapGestures(onTap = { _ -> onClick() }, onLongPress = { _ -> onLongClick() }) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiSelecting) {
            Checkbox(checked = selected, onCheckedChange = null)
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = song.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${song.pageCount} pages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButtonNavigation(
                onClick = { menuExpanded = true },
                painter = R.drawable.ic_more_vert,
                desc = R.string.song_menu_desc,
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = { onRename(song); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { onDelete(song); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_playlist)) },
                    onClick = { onAddToPlaylist(song); menuExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.no_songs_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_songs_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onImport) { Text(stringResource(R.string.import_import)) }
    }
}

@Composable
private fun NoResults(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.no_results_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_results_subtitle, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.rename_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.rename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteConfirmDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.delete_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = stringResource(R.string.delete_confirm_message, count),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun IconButtonNavigation(
    onClick: () -> Unit,
    painter: Int,
    desc: Int,
) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(painterResource(painter), stringResource(desc))
    }
}
