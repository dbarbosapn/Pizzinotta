package com.github.dbarbosapn.pizzinotta.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Custom rendering surface for the reader. A single dedicated thread owns all drawing via locks; it
 * redraws whenever the current frame or the surface size changes. All state is pushed in from the
 * main thread through [setRenderFrame] / [setSurfaceSize].
 *
 * Uses a [TextureView] rather than an [android.view.SurfaceView]: a SurfaceView draws on its own
 * window layer above the app's view tree, so it ignores parent alpha and cannot be cross-faded during
 * navigation — it stays opaque and pops abruptly when finally detached. A TextureView is composited
 * in-process, so it fades with the rest of the screen.
 */
@SuppressLint("ClickableViewAccessibility")
class ReaderSurfaceView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {

    var onGesture: ((ReaderGesture) -> Unit)? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            forwardTap(e.x) { ReaderGesture.Tap(it) }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            forwardTap(e.x) { ReaderGesture.DoubleTap(it) }
            return true
        }
    })

    init {
        surfaceTextureListener = this
        setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lock = ReentrantLock()
    private val cond: Condition = lock.newCondition()
    private val scheduler = RenderScheduler()

    @Volatile
    private var renderFrame: Bitmap? = null

    @Volatile
    private var surfaceWidth = 0

    @Volatile
    private var surfaceHeight = 0

    private val renderThread = RenderThread()

    @Volatile
    private var running = false

    fun setRenderFrame(bitmap: Bitmap?) {
        lock.withLock {
            renderFrame = bitmap
            cond.signalAll()
        }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        lock.withLock {
            surfaceWidth = width
            surfaceHeight = height
            cond.signalAll()
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        running = true
        if (!renderThread.isAlive) renderThread.start()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("reader-debug", "surfaceChanged size=$width x $height")
        setSurfaceSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopRender()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRender()
    }

    private fun stopRender() {
        lock.withLock {
            running = false
            cond.signalAll()
        }
        scheduler.requestStop()
        if (renderThread.isAlive) renderThread.join(JOIN_TIMEOUT_MS)
    }

    private inner class RenderThread : Thread() {
        override fun run() {
            while (true) {
                var frame: Bitmap? = null
                var width = 0
                var height = 0
                var dirty = false
                lock.withLock {
                    if (!running) return
                    frame = renderFrame
                    width = surfaceWidth
                    height = surfaceHeight
                    dirty = scheduler.needsRedraw(frame, width, height)
                    cond.await(RENDER_INTERVAL_MS, TimeUnit.MILLISECONDS)
                }
                if (dirty && running && frame != null && width > 0 && height > 0) {
                    drawFrame(frame, width, height)
                }
            }
        }
    }

    private fun drawFrame(bitmap: Bitmap, width: Int, height: Int) {
        Log.d("reader-debug", "drawFrame surface=$width x $height bitmap=${bitmap.width} x ${bitmap.height}")
        val canvas = lockCanvas() ?: run {
            Log.d("reader-debug", "lockCanvas returned null")
            return
        }
        try {
            canvas.drawColor(Color.BLACK)
            val fit = PageLayout.fit(bitmap.width, bitmap.height, width, height)
            val destination = Rect(
                fit.offsetX,
                fit.offsetY,
                fit.offsetX + fit.renderWidth,
                fit.offsetY + fit.renderHeight,
            )
            canvas.drawBitmap(bitmap, null, destination, paint)
        } finally {
            unlockCanvasAndPost(canvas)
        }
    }

    private fun forwardTap(x: Float, build: (Zone) -> ReaderGesture) {
        if (surfaceWidth <= 0) return
        onGesture?.invoke(build(TapZone.at(x, surfaceWidth)))
    }

    companion object {
        private const val RENDER_INTERVAL_MS = 16L
        private const val JOIN_TIMEOUT_MS = 500L
    }
}
