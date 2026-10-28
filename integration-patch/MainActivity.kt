package com.example.motogyro

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** APRIL settings shell, backed by the same preferences read by WallpaperRenderer. */
class MainActivity : Activity() {
    private lateinit var gyroSlider: SeekBar
    private lateinit var parallaxSlider: SeekBar
    private lateinit var motionSlider: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (saveIncomingSettings(intent)) {
            Toast.makeText(this, "APRIL settings saved", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        showSettings()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (saveIncomingSettings(intent)) {
            Toast.makeText(this, "APRIL settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showSettings() {
        val current = MotionSettingsStore.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(0xFF101216.toInt())
        }

        root.addView(TextView(this).apply {
            text = "APRIL • Motion Settings"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "Adjust the existing 3D wallpaper response"
            textSize = 14f
            setTextColor(0xFFB9C0CA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }, matchWrap())

        addSlider(root, "Gyroscope Sensitivity", current.gyroSensitivity) { gyroSlider = it }
        addSlider(root, "Parallax Depth", current.parallaxDepth) { parallaxSlider = it }
        addSlider(root, "Motion Strength", current.motionStrength) { motionSlider = it }

        root.addView(Button(this).apply {
            text = "Save Settings"
            setOnClickListener {
                saveSliders()
                Toast.makeText(this@MainActivity, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            }
        }, buttonParams())

        root.addView(Button(this).apply {
            text = "Set APRIL Live Wallpaper"
            setOnClickListener {
                saveSliders()
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, GyroWallpaperService::class.java)
                    )
                })
            }
        }, buttonParams())

        root.addView(TextView(this).apply {
            text = "Settings change the existing renderer. Artwork and depth layers stay unchanged."
            textSize = 12f
            setTextColor(0xFFB9C0CA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }, matchWrap())

        setContentView(root)
    }

    private fun addSlider(
        root: LinearLayout,
        label: String,
        initial: Int,
        assign: (SeekBar) -> Unit
    ) {
        val heading = TextView(this).apply {
            text = "$label: $initial%"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(heading, matchWrap())

        val slider = SeekBar(this).apply {
            max = 100
            progress = initial.coerceIn(0, 100)
            contentDescription = label
        }
        assign(slider)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                heading.text = "$label: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(slider, matchWrap())
    }

    private fun saveSliders() {
        MotionSettingsStore.save(this, gyroSlider.progress, parallaxSlider.progress, motionSlider.progress)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
    private fun buttonParams() = matchWrap().apply { topMargin = dp(12) }
}
