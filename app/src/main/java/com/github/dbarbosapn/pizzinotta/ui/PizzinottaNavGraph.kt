package com.github.dbarbosapn.pizzinotta.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.dbarbosapn.pizzinotta.PizzinottaApplication
import com.github.dbarbosapn.pizzinotta.R
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryScreen
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryViewModel
import com.github.dbarbosapn.pizzinotta.ui.nav.Routes
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistDetailScreen
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistDetailViewModel
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistDetailViewModelFactory
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistsScreen
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistsViewModel
import java.util.UUID

private const val ARG_PLAYLIST_ID = "playlistId"
private const val ARG_START_INDEX = "startIndex"

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
        ) {
            ComingSoonScreen(titleRes = R.string.reader, onBack = { navController.navigateUp() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComingSoonScreen(
    titleRes: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Text(
            text = stringResource(R.string.coming_soon),
            modifier = Modifier.padding(padding).padding(24.dp),
        )
    }
}
