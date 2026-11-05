package com.github.dbarbosapn.pizzinotta.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.dbarbosapn.pizzinotta.R

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val surface = remember { ReaderSurfaceView(context).also { it.onGesture = viewModel::onGesture } }

    LaunchedEffect(viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.renderFrame.collect { frame -> surface.setRenderFrame(frame) }
        }
    }

    ReaderImmersiveFlags()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { surface },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> surface.setSurfaceSize(size.width, size.height) },
        )
        if (state.barVisible) {
            ReaderBottomBar(
                state = state,
                onBack = onBack,
                onPreviousSong = viewModel::previousSong,
                onNextSong = viewModel::nextSong,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReaderImmersiveFlags() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val activity = contextAsActivity(view.context) ?: return
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    hideSystemBars(activity.window)
                }
                Lifecycle.Event.ON_STOP -> {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    showSystemBars(activity.window)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun hideSystemBars(window: android.view.Window) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.systemBars())
}

private fun showSystemBars(window: android.view.Window) {
    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
}

private fun contextAsActivity(context: android.content.Context): Activity? =
    context as? Activity

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState,
    onBack: () -> Unit,
    onPreviousSong: () -> Unit,
    onNextSong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = state.title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = stringResource(R.string.reader_page_of, state.pageIndex + 1, state.pageCount),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.hasQueue) {
                Text(
                    text = stringResource(R.string.reader_queue_of, state.songIndex + 1, state.queueSize),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onPreviousSong, enabled = !state.isFirstSong) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.reader_previous_song),
                )
            }
            IconButton(onClick = onNextSong, enabled = !state.isLastSong) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = stringResource(R.string.reader_next_song),
                )
            }
        }
    }
}
