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
            // A wallpaper surface can be recreated without the Engine itself
            // being destroyed. Never leave the previous renderer/thread alive.
            renderer?.stop()
            renderer = WallpaperRenderer(this@AprilWallpaperService, holder)
            surfaceReady = holder.surface?.isValid == true
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
            mainHandler.removeCallbacksAndMessages(null)
            val old = renderer
            renderer = null
            old?.stop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            visible = isVisible
            if (!isVisible) {
                // Stop immediately when Android takes the wallpaper off-screen.
                renderer?.stop()
            } else {
                mainHandler.post { syncRenderer() }
            }
        }

        override fun onDestroy() {
            surfaceReady = false
            mainHandler.removeCallbacksAndMessages(null)
            val old = renderer
            renderer = null
            old?.stop()
            super.onDestroy()
        }

        private fun syncRenderer() {
            val current = renderer ?: return
            if (surfaceReady && visible && currentSurfaceIsValid()) {
                current.start()
            } else {
                current.stop()
            }
        }

        private fun currentSurfaceIsValid(): Boolean = try {
            renderer != null
        } catch (_: Exception) {
            false
        }
    }
}
