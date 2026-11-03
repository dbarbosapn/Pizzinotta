package com.github.dbarbosapn.pizzinotta

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.dbarbosapn.pizzinotta.data.import.ImportResult
import com.github.dbarbosapn.pizzinotta.data.import.ImportSource
import com.github.dbarbosapn.pizzinotta.data.import.UriImportSource
import com.github.dbarbosapn.pizzinotta.ui.PizzinottaNavGraph
import com.github.dbarbosapn.pizzinotta.ui.import.ImportViewModel
import com.github.dbarbosapn.pizzinotta.ui.import.ImportViewModelFactory
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryViewModel
import com.github.dbarbosapn.pizzinotta.ui.library.LibraryViewModelFactory
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistsViewModel
import com.github.dbarbosapn.pizzinotta.ui.playlists.PlaylistsViewModelFactory
import com.github.dbarbosapn.pizzinotta.ui.theme.PizzinottaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val importViewModel by lazy {
        ViewModelProvider(this, ImportViewModelFactory(application))[ImportViewModel::class.java]
    }

    private val libraryViewModel by lazy {
        ViewModelProvider(this, LibraryViewModelFactory(application))[LibraryViewModel::class.java]
    }

    private val playlistsViewModel by lazy {
        ViewModelProvider(this, PlaylistsViewModelFactory(application))[PlaylistsViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PizzinottaTheme {
                PizzinottaNavGraph(
                    libraryViewModel = libraryViewModel,
                    playlistsViewModel = playlistsViewModel,
                )
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
