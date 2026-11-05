package com.github.dbarbosapn.pizzinotta.ui.library

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import com.github.dbarbosapn.pizzinotta.data.domain.Playlist
import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.import.ImportResult
import com.github.dbarbosapn.pizzinotta.data.import.UriImportSource
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToPlaylists: () -> Unit,
    onOpenSong: (startIndex: Int, query: String, order: SongOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val playlistCounts by viewModel.playlistCounts.collectAsState(initial = emptyMap())
    val songPlaylists by viewModel.songPlaylists.collectAsState(initial = emptyMap())
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
    var addToPlaylistTargets by remember { mutableStateOf<List<Song>?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            when {
                ui.multiSelecting -> MultiSelectTopBar(
                    count = ui.selection.size,
                    onClose = viewModel::clearSelection,
                    onAddToPlaylist = {
                        val targets = ui.songs.filter { it.id in ui.selection }
                        if (targets.isNotEmpty()) addToPlaylistTargets = targets
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
                            playlists = songPlaylists[song.id] ?: emptyList(),
                            onClick = {
                                if (ui.multiSelecting) {
                                    viewModel.toggleSelection(song.id)
                                } else {
                                    val index = ui.songs.indexOfFirst { it.id == song.id }
                                    if (index >= 0) onOpenSong(index, ui.searchQuery, ui.order)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(song.id) },
                            onRename = { target -> renameTarget = target },
                            onDelete = { deleteTarget = it },
                            onAddToPlaylist = { addToPlaylistTargets = listOf(it) },
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

    addToPlaylistTargets?.let { targets ->
        AddToPlaylistSheet(
            playlists = playlists,
            counts = playlistCounts,
            onDismiss = { addToPlaylistTargets = null },
            onPickPlaylist = { playlist ->
                addToPlaylistTargets = null
                scope.launch {
                    for ((id) in targets) viewModel.addToPlaylist(id, playlist.id)
                    snackbarHostState.showSnackbar(resString(R.string.added_to_playlist, playlist.name))
                }
            },
            onCreatePlaylist = { newName ->
                addToPlaylistTargets = null
                scope.launch {
                    val created = viewModel.createPlaylist(newName)
                    if (created != null) {
                        for ((id) in targets) viewModel.addToPlaylist(id, created.id)
                        snackbarHostState.showSnackbar(resString(R.string.added_to_playlist, created.name))
                    }
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
    playlists: List<String>,
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
            if (playlists.isNotEmpty()) {
                Text(
                    text = playlists.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    counts: Map<UUID, Int>,
    onDismiss: () -> Unit,
    onPickPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var creatingNew by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.add_to_playlist_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (playlists.isEmpty()) {
                Text(
                    text = stringResource(R.string.playlists_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { onPickPlaylist(playlist) })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val count = counts[playlist.id] ?: 0
                            Text(
                                text = if (count == 1) "1 song" else "$count songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (creatingNew) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.playlist_name_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onCreatePlaylist(name) }) {
                        Text(stringResource(R.string.save))
                    }
                }
            } else {
                TextButton(
                    onClick = { creatingNew = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.new_playlist)) }
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
