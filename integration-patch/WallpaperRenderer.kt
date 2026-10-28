package com.example.motogyro

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Choreographer
import android.view.SurfaceHolder
import kotlin.math.max

/** Six transparent planes composited with depth-scaled gyro parallax. */
class WallpaperRenderer(
    private val context: Context,
    private val holder: SurfaceHolder
) : Choreographer.FrameCallback {

    private val motion = MotionController(context)
    private val preferences = context.getSharedPreferences(
        MotionSettingsStore.PREFERENCES_NAME_FOR_LISTENER,
        Context.MODE_PRIVATE
    )
    @Volatile private var settings = MotionSettingsStore.load(context)
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        settings = MotionSettingsStore.load(context)
        motion.setSensitivity(settings.gyroSensitivity)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val layers: List<Bitmap> = listOf(
        load(context, R.drawable.depth_layer_1),
        load(context, R.drawable.depth_layer_2),
        load(context, R.drawable.depth_layer_3),
        load(context, R.drawable.depth_layer_4),
        load(context, R.drawable.depth_layer_5),
        load(context, R.drawable.glass_reflection)
    )
    // Stronger physical separation while preserving the existing six-layer renderer.
    private val depth = floatArrayOf(0.8f, 2.2f, 4.5f, 7.5f, 11.0f, 13.0f)
    private var width = 1
    private var height = 1
    private var running = false
    private var lastFrameNanos = 0L

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        settings = MotionSettingsStore.load(context)
        motion.setSensitivity(settings.gyroSensitivity)
        preferences.registerOnSharedPreferenceChangeListener(settingsListener)
        motion.start()
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        preferences.unregisterOnSharedPreferenceChangeListener(settingsListener)
        motion.stop()
        Choreographer.getInstance().removeFrameCallback(this)
        lastFrameNanos = 0L
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrameNanos == 0L) 1f / 60f
            else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = frameTimeNanos
        motion.update(dt)
        drawFrame()
        if (running) Choreographer.getInstance().postFrameCallback(this)
    }

    private fun drawFrame() {
        val canvas = try { holder.lockCanvas() } catch (_: Exception) { null } ?: return
        try {
            canvas.drawColor(android.graphics.Color.BLACK)
            val currentSettings = settings
            layers.forEachIndexed { index, bitmap ->
                drawLayer(bitmap, depth[index], currentSettings, canvas)
            }
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
        }
    }

    private fun drawLayer(
        bitmap: Bitmap,
        depthMultiplier: Float,
        currentSettings: MotionSettings,
        canvas: Canvas
    ) {
        val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height) * 1.18f
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val bx = (width - dw) / 2f
        val by = (height - dh) / 2f
        // Defaults (gyro 50, parallax 60, motion 40) remain the baseline response.
        // The physical range is intentionally larger so 100% visibly moves the layers.
        val parallaxScale = currentSettings.parallaxDepth / 60f
        val motionScale = currentSettings.motionStrength / 40f
        val sx = motion.x * depthMultiplier * parallaxScale * motionScale * width * 0.055f
        val sy = motion.y * depthMultiplier * parallaxScale * motionScale * height * 0.055f
        canvas.drawBitmap(bitmap, null, RectF(bx - sx, by - sy, bx + dw - sx, by + dh - sy), paint)
    }

    private fun load(context: Context, id: Int): Bitmap =
        requireNotNull(BitmapFactory.decodeResource(context.resources, id)) {
            "Could not decode wallpaper layer resource $id"
        }
}
