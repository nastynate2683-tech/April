package app.floatdeck.data

import android.content.Context

/**
 * Adapter for the eventual combined FloatDeck-shell + APRIL-engine APK.
 *
 * Keep this in the FloatDeck package when porting the shell. It writes to both:
 * 1) FloatDeck's existing preference keys, used by its settings/wallpaper code.
 * 2) APRIL's preference file and keys, read by the APRIL layered renderer.
 *
 * This adapter does not replace either renderer or modify any artwork. The settings
 * screen must call save() with its current slider values for the bridge to be active.
 */
object AprilSettingsBridge {
    private const val FLOATDECK_PREFS = "settings_prefs"
    private const val APRIL_PREFS = "anonymous_clockstopper_motion"

    private const val KEY_TILT_SENSITIVITY = "tilt_sensitivity"
    private const val KEY_SWAY_AMPLITUDE = "sway_amplitude"

    private const val KEY_GYRO = "gyro_sensitivity"
    private const val KEY_PARALLAX = "parallax_depth"
    private const val KEY_MOTION = "motion_strength"

    data class Values(
        val gyro: Int = 50,
        val parallax: Int = 60,
        val motion: Int = 40,
    ) {
        fun normalized() = Values(
            gyro = gyro.coerceIn(0, 100),
            parallax = parallax.coerceIn(0, 100),
            motion = motion.coerceIn(0, 100),
        )
    }

    fun save(context: Context, values: Values) {
        val safe = values.normalized()

        // APRIL layered renderer: preserve its own independent 0..100 values.
        context.getSharedPreferences(APRIL_PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_GYRO, safe.gyro)
            .putInt(KEY_PARALLAX, safe.parallax)
            .putInt(KEY_MOTION, safe.motion)
            .apply()

        // FloatDeck shell: 50% gyro and 40% motion retain neutral/default response.
        // Parallax depth is also persisted for the APRIL renderer; FloatDeck's
        // renderer needs a separate coefficient hook before it can use this value.
        context.getSharedPreferences(FLOATDECK_PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_TILT_SENSITIVITY, safe.gyro / 50f)
            .putFloat(KEY_SWAY_AMPLITUDE, safe.motion / 40f)
            .putInt(KEY_PARALLAX, safe.parallax)
            .apply()
    }

    fun load(context: Context): Values {
        val prefs = context.getSharedPreferences(APRIL_PREFS, Context.MODE_PRIVATE)
        return Values(
            gyro = prefs.getInt(KEY_GYRO, 50),
            parallax = prefs.getInt(KEY_PARALLAX, 60),
            motion = prefs.getInt(KEY_MOTION, 40),
        ).normalized()
    }
}
