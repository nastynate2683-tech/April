package com.example.motogyro

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import kotlin.math.max

/**
 * APRIL renderer. The original six-layer artwork remains the fallback/demo.
 * Uploaded images use the continuous TurboDepthRenderer so parallax changes
 * depth separation rather than translating the entire picture off-screen.
 */
class WallpaperRenderer(
    private val context: Context,
    private val holder: SurfaceHolder
) {
    private val motion = MotionController(context)
    private val preferences = context.getSharedPreferences(
        MotionSettingsStore.PREFERENCES_NAME_FOR_LISTENER, Context.MODE_PRIVATE
    )
    @Volatile private var settings = MotionSettingsStore.load(context)
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        settings = MotionSettingsStore.load(context)
        motion.setSensitivity(settings.gyroSensitivity)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val layers: List<Bitmap> = listOf(
        load(context, R.drawable.depth_layer_1),
        load(context, R.drawable.depth_layer_2),
        load(context, R.drawable.depth_layer_3),
        load(context, R.drawable.depth_layer_4),
        load(context, R.drawable.depth_layer_5),
        load(context, R.drawable.glass_reflection)
    )
    private val depth = floatArrayOf(4f, 10f, 20f, 35f, 50f, 65f)
    private val customImageFile by lazy { java.io.File(context.filesDir, "april_custom_image.jpg") }
    private var customImage: Bitmap? = null
    private var turbo: TurboDepthRenderer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var width = 1
    private var height = 1
    @Volatile private var running = false
    private var lastFrameNanos = 0L
    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running || turbo != null) return
            val now = System.nanoTime()
            val dt = if (lastFrameNanos == 0L) 1f / 60f
                else ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = now
            motion.update(dt)
            drawFrame()
            if (running && turbo == null) handler.postDelayed(this, 16L)
        }
    }

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        turbo?.resize(width, height)
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        settings = MotionSettingsStore.load(context)
        motion.setSensitivity(settings.gyroSensitivity)
        val customEnabled = preferences.getBoolean("use_custom_image", false) && customImageFile.exists()
        preferences.registerOnSharedPreferenceChangeListener(settingsListener)
        motion.start()
        if (customEnabled) {
            customImage = null
            turbo = TurboDepthRenderer(holder, motion, { settings }, customImageFile).also {
                it.resize(width, height)
                it.start()
            }
        } else {
            customImage = null
            handler.removeCallbacks(frameRunnable)
            handler.post(frameRunnable)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        preferences.unregisterOnSharedPreferenceChangeListener(settingsListener)
        turbo?.stop()
        turbo = null
        customImage = null
        motion.stop()
        handler.removeCallbacks(frameRunnable)
        lastFrameNanos = 0L
    }

    private fun drawFrame() {
        val canvas = try { holder.lockCanvas() } catch (_: Exception) { null } ?: return
        try {
            canvas.drawColor(Color.BLACK)
            val currentSettings = settings
            layers.forEachIndexed { index, bitmap ->
                drawLayer(bitmap, depth[index], currentSettings, canvas)
            }
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
        }
    }

    private fun drawLayer(bitmap: Bitmap, depthMultiplier: Float, current: MotionSettings, canvas: Canvas) {
        val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height) * 1.18f
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val bx = (width - dw) / 2f
        val by = (height - dh) / 2f
        val parallaxScale = current.parallaxDepth / 60f
        val motionScale = current.motionStrength / 40f
        val sx = (motion.x * depthMultiplier * parallaxScale * motionScale * width * 0.20f)
            .coerceIn(-width * 0.035f, width * 0.035f)
        val sy = (motion.y * depthMultiplier * parallaxScale * motionScale * height * 0.20f)
            .coerceIn(-height * 0.035f, height * 0.035f)
        canvas.drawBitmap(bitmap, null, RectF(bx - sx, by - sy, bx + dw - sx, by + dh - sy), paint)
    }

    private fun load(context: Context, id: Int): Bitmap =
        requireNotNull(BitmapFactory.decodeResource(context.resources, id)) {
            "Could not decode wallpaper layer resource $id"
        }
}
