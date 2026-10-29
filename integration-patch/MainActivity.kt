package com.example.motogyro

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/** APRIL upload, preview, preset, and motion settings shell. */
class MainActivity : Activity() {
    private lateinit var gyroSlider: SeekBar
    private lateinit var parallaxSlider: SeekBar
    private lateinit var motionSlider: SeekBar
    private lateinit var preview: ImageView
    private lateinit var imageStatus: TextView
    private val pickImageRequest = 411
    private val customImageFile by lazy { File(filesDir, "april_custom_image.jpg") }

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
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFF101216.toInt())
        }
        content.addView(TextView(this).apply {
            text = "APRIL • TURBO 3D"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, compactWrap())
        content.addView(TextView(this).apply {
            text = "Choose an image, pick a speed, then apply."
            textSize = 12f
            setTextColor(0xFFB9C0CA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }, compactWrap())

        preview = ImageView(this).apply {
            // Show the complete scene in the settings preview. depth_layer_1 is
            // a transparent depth layer, not the finished APRIL artwork.
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF050505.toInt())
            contentDescription = "APRIL image preview"
        }
        content.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(170)
        ))
        imageStatus = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFB9C0CA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(2))
        }
        content.addView(imageStatus, compactWrap())
        refreshPreview()

        val uploadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        uploadRow.addView(ImageButton(this).apply {
            contentDescription = "Upload picture"
            setImageResource(android.R.drawable.ic_menu_upload)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { openImagePicker() }
        }, LinearLayout.LayoutParams(dp(46), dp(42)))
        uploadRow.addView(TextView(this).apply {
            text = "Upload picture"
            textSize = 12f
            setTextColor(0xFFB9C0CA.toInt())
            setPadding(dp(4), 0, 0, 0)
        }, compactWrap())
        content.addView(uploadRow, compactWrap())

        content.addView(TextView(this).apply {
            text = "MOTION PRESETS"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFF6B35.toInt())
            setPadding(0, dp(5), 0, dp(1))
        }, compactWrap())
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("SLOW", "MEDIUM", "TURBO").forEachIndexed { index, name ->
            presets.addView(Button(this).apply {
                text = name
                textSize = 11f
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener {
                    when (index) {
                        0 -> setSliders(25, 20, 20)
                        1 -> setSliders(50, 50, 40)
                        else -> setSliders(80, 70, 65)
                    }
                    saveSliders()
                    Toast.makeText(this@MainActivity, "$name preset saved", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginEnd = dp(3)
            })
        }
        content.addView(presets, compactWrap())

        content.addView(Button(this).apply {
            text = "APPLY & CONTINUE"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setOnClickListener {
                saveSliders()
                openLiveWallpaperChooser()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
            topMargin = dp(5)
            bottomMargin = dp(3)
        })

        addSlider(content, "Gyroscope Sensitivity", current.gyroSensitivity) { gyroSlider = it }
        addSlider(content, "Parallax Depth", current.parallaxDepth) { parallaxSlider = it }
        addSlider(content, "Motion Strength", current.motionStrength) { motionSlider = it }

        content.addView(Button(this).apply {
            text = "Save Settings"
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                saveSliders()
                Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply {
            topMargin = dp(2)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        setContentView(scroll)
    }

    private fun openImagePicker() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(pick, pickImageRequest)
    }

    @Deprecated("Use system photo picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickImageRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open selected image" }.use { source ->
                    FileOutputStream(customImageFile).use { target -> source.copyTo(target) }
                }
            }
            getSharedPreferences("anonymous_clockstopper_motion", MODE_PRIVATE).edit()
                .putBoolean("use_custom_image", true).apply()
            refreshPreview()
            Toast.makeText(this, "Image loaded into APRIL", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load that image: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshPreview() {
        if (customImageFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(customImageFile.absolutePath)
            if (bitmap != null) {
                preview.setImageBitmap(bitmap)
                imageStatus.text = "Custom image ready • ${bitmap.width} × ${bitmap.height}"
                return
            }
        }
        // The original project contains a complete finished scene in scene_color.
        // depth_layer_1 is intentionally only one transparent depth layer and must
        // never be used as the user-facing preview.
        preview.setImageResource(R.drawable.scene_color)
        imageStatus.text = "APRIL original demo artwork"
    }

    private fun setSliders(gyro: Int, parallax: Int, motion: Int) {
        gyroSlider.progress = gyro
        parallaxSlider.progress = parallax
        motionSlider.progress = motion
    }

    private fun openLiveWallpaperChooser() {
        val service = ComponentName(this, AprilWallpaperService::class.java)
        try {
            // Prefer Android's direct "set this live wallpaper" flow. This avoids
            // depending on a Motorola wallpaper-picker screen that may be hidden.
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, service)
            })
        } catch (_: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Exception) {
                Toast.makeText(this, "Android live wallpapers are unavailable on this device", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addSlider(root: LinearLayout, label: String, initial: Int, assign: (SeekBar) -> Unit) {
        val heading = TextView(this).apply {
            text = "$label: $initial%"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, dp(3), 0, 0)
        }
        root.addView(heading, compactWrap())
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
        root.addView(slider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(34)
        ))
    }

    private fun saveSliders() {
        MotionSettingsStore.save(this, gyroSlider.progress, parallaxSlider.progress, motionSlider.progress)
    }

    private fun saveIncomingSettings(incoming: Intent?): Boolean {
        if (incoming?.action != Intent.ACTION_VIEW) return false
        val uri: Uri = incoming.data ?: return false
        if (uri.scheme != "april" || uri.host != "settings") return false
        val current = MotionSettingsStore.load(this)
        MotionSettingsStore.save(
            this,
            uri.getQueryParameter("gyro")?.toIntOrNull() ?: current.gyroSensitivity,
            uri.getQueryParameter("parallax")?.toIntOrNull() ?: current.parallaxDepth,
            uri.getQueryParameter("motion")?.toIntOrNull() ?: current.motionStrength
        )
        return true
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun compactWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
