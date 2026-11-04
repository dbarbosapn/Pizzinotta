package com.github.dbarbosapn.pizzinotta.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.dbarbosapn.pizzinotta.PizzinottaApplication
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryScreen
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryViewModel
import com.github.dbarbosapn.pizzinotta.ui.nav.Routes
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistDetailScreen
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistDetailViewModel
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistDetailViewModelFactory
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistsScreen
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.reader.LibraryQueueLoader
import com.github.dbarbosapn.pizzinotta.data.reader.PlaylistQueueLoader
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistsViewModel
import com.github.dbarbosapn.pizzinotta.ui.reader.ReaderScreen
import com.github.dbarbosapn.pizzinotta.ui.reader.ReaderViewModel
import com.github.dbarbosapn.pizzinotta.ui.reader.ReaderViewModelFactory
import java.util.UUID

private const val ARG_PLAYLIST_ID = "playlistId"
private const val ARG_START_INDEX = "startIndex"
private const val ARG_QUERY = "query"
private const val ARG_ORDER = "order"

@Composable
fun PizzinottaNavGraph(
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onNavigateToPlaylists = { navController.navigate(Routes.PLAYLISTS) },
                onOpenSong = { startIndex, query, order -> navController.navigate(Routes.libraryReaderRoute(startIndex, query, order)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(Routes.PLAYLISTS) {
            PlaylistsScreen(
                viewModel = playlistsViewModel,
                onNavigateToDetail = { navController.navigate(Routes.playlistDetailRoute(it)) },
                onBack = { navController.navigateUp() },
            )
        }
        composable(
            route = "playlistDetail/{$ARG_PLAYLIST_ID}",
            arguments = listOf(navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val playlistId = UUID.fromString(backStackEntry.arguments?.getString(ARG_PLAYLIST_ID))
            val app = LocalContext.current.applicationContext as PizzinottaApplication
            val viewModel: PlaylistDetailViewModel = viewModel(factory = PlaylistDetailViewModelFactory(playlistId, app))
            PlaylistDetailScreen(
                viewModel = viewModel,
                onNavigateToReader = { startIndex -> navController.navigate(Routes.readerRoute(playlistId, startIndex)) },
                onDeleted = { navController.navigateUp() },
                onBack = { navController.navigateUp() },
            )
        }
        composable(
            route = "reader/{$ARG_PLAYLIST_ID}/{$ARG_START_INDEX}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType },
                navArgument(ARG_START_INDEX) { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val playlistId = UUID.fromString(backStackEntry.arguments?.getString(ARG_PLAYLIST_ID))
            val startIndex = backStackEntry.arguments?.getInt(ARG_START_INDEX) ?: 0
            val app = LocalContext.current.applicationContext as PizzinottaApplication
            val loader = PlaylistQueueLoader(app.database.playlistDao(), playlistId)
            val viewModel: ReaderViewModel = viewModel(factory = ReaderViewModelFactory(loader, startIndex))
            ReaderScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable(
            route = "readerLibrary/{$ARG_START_INDEX}/{$ARG_QUERY}/{$ARG_ORDER}",
            arguments = listOf(
                navArgument(ARG_START_INDEX) { type = NavType.IntType },
                navArgument(ARG_QUERY) { type = NavType.StringType },
                navArgument(ARG_ORDER) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getInt(ARG_START_INDEX) ?: 0
            val query = java.net.URLDecoder.decode(backStackEntry.arguments?.getString(ARG_QUERY) ?: "", "UTF-8")
            val order = runCatching { SongOrder.valueOf(backStackEntry.arguments?.getString(ARG_ORDER) ?: "") }
                .getOrDefault(SongOrder.TITLE)
            val app = LocalContext.current.applicationContext as PizzinottaApplication
            val loader = LibraryQueueLoader(app.database.songDao(), query, order)
            val viewModel: ReaderViewModel = viewModel(factory = ReaderViewModelFactory(loader, startIndex))
            ReaderScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
    }
}
