package com.example.motogyro

import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class AprilWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AprilEngine()

    private inner class AprilEngine : Engine() {
        private val mainHandler = Handler(Looper.getMainLooper())
        private var renderer: WallpaperRenderer? = null
        private var surfaceReady = false
        private var visible = true

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer = WallpaperRenderer(this@AprilWallpaperService, holder)
            surfaceReady = true
            // Android can call onSurfaceCreated before the preview surface has
            // its final dimensions. Draw once after the surface is ready, then
            // let the renderer take over the animated frame loop.
            mainHandler.post { syncRenderer() }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer?.resize(width, height)
            surfaceReady = holder.surface?.isValid == true
            mainHandler.post { syncRenderer() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            renderer?.stop()
            renderer = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            visible = isVisible
            mainHandler.post { syncRenderer() }
        }

        override fun onDestroy() {
            surfaceReady = false
            renderer?.stop()
            renderer = null
            mainHandler.removeCallbacksAndMessages(null)
            super.onDestroy()
        }

        private fun syncRenderer() {
            val current = renderer ?: return
            if (surfaceReady && visible) {
                current.start()
            } else {
                current.stop()
            }
        }
    }
}
