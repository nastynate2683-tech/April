package com.example.motogyro

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (saveIncomingSettings(intent)) {
            // Return to the calling companion app after accepting settings.
            // The live wallpaper reads the shared preferences independently.
            finish()
            return
        }

        startActivity(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, GyroWallpaperService::class.java)
                )
            }
        )
        finish()
    }

    private fun saveIncomingSettings(incoming: Intent?): Boolean {
        if (incoming?.action != Intent.ACTION_VIEW) return false
        val uri: Uri = incoming.data ?: return false
        if (uri.scheme != "april" || uri.host != "settings") return false

        val current = MotionSettingsStore.load(this)
        val gyro = uri.getQueryParameter("gyro")?.toIntOrNull() ?: current.gyroSensitivity
        val parallax = uri.getQueryParameter("parallax")?.toIntOrNull() ?: current.parallaxDepth
        val motion = uri.getQueryParameter("motion")?.toIntOrNull() ?: current.motionStrength

        MotionSettingsStore.save(this, gyro, parallax, motion)
        return true
    }
}
