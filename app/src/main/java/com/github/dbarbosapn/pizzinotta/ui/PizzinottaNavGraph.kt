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
import com.github.dbarbosapn.pizzinotta.R
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryScreen
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryViewModel
import com.github.dbarbosapn.pizzinotta.ui.nav.Routes

private const val ARG_PLAYLIST_ID = "playlistId"

@Composable
fun PizzinottaNavGraph(
    libraryViewModel: LibraryViewModel,
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
            ComingSoonScreen(titleRes = R.string.playlists, onBack = { navController.navigateUp() })
        }
        composable(
            route = "playlistDetail/{$ARG_PLAYLIST_ID}",
            arguments = listOf(navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType }),
        ) {
            ComingSoonScreen(titleRes = R.string.playlist, onBack = { navController.navigateUp() })
        }
        composable(
            route = "reader/{$ARG_PLAYLIST_ID}",
            arguments = listOf(navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType }),
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
