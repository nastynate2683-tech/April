package com.example.motogyro

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            setBackgroundColor(0xFF101216.toInt())
        }
        root.addView(TextView(this).apply {
            text = "APRIL • TURBO 3D"
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "Choose an image, preview it, then tune its motion."
            textSize = 14f
            setTextColor(0xFFB9C0CA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }, matchWrap())

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF050505.toInt())
            contentDescription = "APRIL image preview"
        }
        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(230)
        ))
        imageStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFB9C0CA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(4))
        }
        root.addView(imageStatus, matchWrap())
        refreshPreview()

        root.addView(Button(this).apply {
            text = "Upload Picture"
            setOnClickListener { openImagePicker() }
        }, buttonParams())

        root.addView(TextView(this).apply {
            text = "MOTION PRESETS"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFF6B35.toInt())
            setPadding(0, dp(16), 0, dp(2))
        }, matchWrap())
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("SLOW", "MEDIUM", "TURBO").forEachIndexed { index, name ->
            presets.addView(Button(this).apply {
                text = name
                setOnClickListener {
                    when (index) {
                        0 -> setSliders(25, 20, 20)
                        1 -> setSliders(50, 50, 40)
                        else -> setSliders(80, 70, 65)
                    }
                    saveSliders()
                    Toast.makeText(this@MainActivity, "$name preset saved", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(4)
            })
        }
        root.addView(presets, matchWrap())

        addSlider(root, "Gyroscope Sensitivity", current.gyroSensitivity) { gyroSlider = it }
        addSlider(root, "Parallax Depth", current.parallaxDepth) { parallaxSlider = it }
        addSlider(root, "Motion Strength", current.motionStrength) { motionSlider = it }

        root.addView(Button(this).apply {
            text = "Save Settings"
            setOnClickListener {
                saveSliders()
                Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "Preview / Set Live Wallpaper"
            setOnClickListener {
                saveSliders()
                openLiveWallpaperChooser()
            }
        }, buttonParams())
        root.addView(TextView(this).apply {
            text = "Uploaded images are stored on this device. Depth generation and GPU rendering are the next engine stage; this build preserves the original six-layer demo."
            textSize = 12f
            setTextColor(0xFFB9C0CA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }, matchWrap())
        setContentView(root)
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
        preview.setImageResource(R.drawable.depth_layer_1)
        imageStatus.text = "APRIL original demo artwork"
    }

    private fun setSliders(gyro: Int, parallax: Int, motion: Int) {
        gyroSlider.progress = gyro
        parallaxSlider.progress = parallax
        motionSlider.progress = motion
    }

    private fun openLiveWallpaperChooser() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (_: Exception) {
            Toast.makeText(this, "Android live wallpapers are unavailable on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun addSlider(root: LinearLayout, label: String, initial: Int, assign: (SeekBar) -> Unit) {
        val heading = TextView(this).apply {
            text = "$label: $initial%"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(3))
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
        MotionSettingsStore.save(
            this,
            uri.getQueryParameter("gyro")?.toIntOrNull() ?: current.gyroSensitivity,
            uri.getQueryParameter("parallax")?.toIntOrNull() ?: current.parallaxDepth,
            uri.getQueryParameter("motion")?.toIntOrNull() ?: current.motionStrength
        )
        return true
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
    private fun buttonParams() = matchWrap().apply { topMargin = dp(8) }
}