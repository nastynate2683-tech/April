package com.example.motogyro

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Native APRIL live-wallpaper engine.
 * Keeps the existing six-layer renderer and only owns its Android wallpaper lifecycle.
 */
class AprilWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AprilEngine()

    private inner class AprilEngine : Engine() {
        private var renderer: WallpaperRenderer? = null
        private var surfaceReady = false
        private var visible = false

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer = WallpaperRenderer(this@AprilWallpaperService, holder)
            surfaceReady = true
            syncRenderer()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer?.resize(width, height)
            syncRenderer()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            renderer?.stop()
            renderer = null
            surfaceReady = false
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            syncRenderer()
        }

        override fun onDestroy() {
            renderer?.stop()
            renderer = null
            surfaceReady = false
            super.onDestroy()
        }

        private fun syncRenderer() {
            val active = surfaceReady && visible
            if (active) renderer?.start() else renderer?.stop()
        }
    }
}
