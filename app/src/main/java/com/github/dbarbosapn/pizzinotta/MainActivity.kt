package com.github.dbarbosapn.pizzinotta

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.dbarbosapn.pizzinotta.data.import.ImportResult
import com.github.dbarbosapn.pizzinotta.data.import.ImportSource
import com.github.dbarbosapn.pizzinotta.data.import.UriImportSource
import com.github.dbarbosapn.pizzinotta.ui.import.ImportViewModel
import com.github.dbarbosapn.pizzinotta.ui.import.ImportViewModelFactory
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryScreen
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryViewModel
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryViewModelFactory
import com.github.dbarbosapn.pizzinotta.ui.nav.Screen
import com.github.dbarbosapn.pizzinotta.ui.theme.PizzinottaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val importViewModel by lazy {
        ViewModelProvider(this, ImportViewModelFactory(application))[ImportViewModel::class.java]
    }

    private val libraryViewModel by lazy {
        ViewModelProvider(this, LibraryViewModelFactory(application))[LibraryViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PizzinottaTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Library) }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    when (screen) {
                        Screen.Library -> LibraryScreen(
                            viewModel = libraryViewModel,
                            onNavigateToPlaylists = { screen = Screen.Playlists },
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                        Screen.Playlists -> PlaylistsPlaceholder(
                            onBack = { screen = Screen.Library },
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                    }
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val sources = pdfSourcesFrom(intent)
        if (sources.isEmpty()) return
        lifecycleScope.launch {
            var imported = 0
            var skipped = 0
            var failed = 0
            importViewModel.importSources(sources).collect { result ->
                when (result) {
                    is ImportResult.Imported -> imported++
                    is ImportResult.Duplicate -> skipped++
                    is ImportResult.Failure -> {
                        failed++
                        Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            val summary = buildString {
                append("Imported $imported")
                if (skipped > 0) append(", skipped $skipped")
                if (failed > 0) append(", failed $failed")
            }
            Toast.makeText(this@MainActivity, summary, Toast.LENGTH_LONG).show()
        }
    }

    private fun pdfSourcesFrom(intent: Intent?): List<ImportSource> {
        val source = intent ?: return emptyList()
        return when (source.action) {
            Intent.ACTION_SEND -> streamUris(source).map { UriImportSource(it, contentResolver) }
            Intent.ACTION_VIEW ->
                source.data?.let { listOf(UriImportSource(it, contentResolver)) } ?: emptyList()
            else -> emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun streamUris(intent: Intent): List<Uri> {
        val extra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, ArrayList::class.java) as? Collection<*>
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Collection<*>
        }
        return extra?.filterIsInstance<Uri>() ?: emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistsPlaceholder(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        TopAppBar(
            title = { Text(stringResource(R.string.playlists)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
            },
        )
        Text(
            text = stringResource(R.string.coming_soon),
            modifier = Modifier.padding(padding).padding(24.dp),
        )
    }
}
