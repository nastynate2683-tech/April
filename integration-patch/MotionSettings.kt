package com.example.motogyro

import android.content.Context

data class MotionSettings(
    val gyroSensitivity: Int = 50,
    val parallaxDepth: Int = 60,
    val motionStrength: Int = 40
)

object MotionSettingsStore {
    private const val PREFS_NAME = "anonymous_clockstopper_motion"
    private const val KEY_GYRO = "gyro_sensitivity"
    private const val KEY_PARALLAX = "parallax_depth"
    private const val KEY_MOTION = "motion_strength"

    fun load(context: Context): MotionSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return MotionSettings(
            gyroSensitivity = prefs.getInt(KEY_GYRO, 50).coerceIn(0, 100),
            parallaxDepth = prefs.getInt(KEY_PARALLAX, 60).coerceIn(0, 100),
            motionStrength = prefs.getInt(KEY_MOTION, 40).coerceIn(0, 100)
        )
    }

    fun save(context: Context, gyroSensitivity: Int, parallaxDepth: Int, motionStrength: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_GYRO, gyroSensitivity.coerceIn(0, 100))
            .putInt(KEY_PARALLAX, parallaxDepth.coerceIn(0, 100))
            .putInt(KEY_MOTION, motionStrength.coerceIn(0, 100))
            .apply()
    }

    const val PREFERENCES_NAME_FOR_LISTENER = PREFS_NAME
}
