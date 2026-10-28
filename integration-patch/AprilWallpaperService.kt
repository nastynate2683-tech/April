package com.example.motogyro

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class AprilWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AprilEngine()

    private inner class AprilEngine : Engine() {
        private var renderer: WallpaperRenderer? = null
        private var surfaceReady = false
        private var visible = true

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

        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            visible = isVisible
            syncRenderer()
        }

        override fun onDestroy() {
            renderer?.stop()
            renderer = null
            surfaceReady = false
            super.onDestroy()
        }

        private fun syncRenderer() {
            if (surfaceReady && visible) renderer?.start() else renderer?.stop()
        }
    }
}
