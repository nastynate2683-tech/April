package com.example.motogyro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.PI
import kotlin.math.exp

/** Rotation-vector motion input with an intentionally strong, visible home-screen response. */
class MotionController(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    @Volatile var x: Float = 0f
        private set
    @Volatile var y: Float = 0f
        private set

    @Volatile private var targetX = 0f
    @Volatile private var targetY = 0f
    @Volatile private var sensitivityScale = 1f
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var registered = false

    fun setSensitivity(percent: Int) {
        // 50% is the neutral setting; 100% is deliberately much more responsive.
        sensitivityScale = (percent.coerceIn(0, 100) / 50f).coerceIn(0f, 2f)
    }

    fun start() {
        val input = sensor ?: return
        if (!registered) {
            registered = manager.registerListener(this, input, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        if (registered) {
            manager.unregisterListener(this)
            registered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // Use a much smaller angular window so normal phone tilts produce a large,
        // obvious back-and-forth parallax response instead of a barely perceptible drift.
        val fullScaleAngle = PI.toFloat() / 12f // 15 degrees
        targetX = ((orientation[2] / fullScaleAngle) * sensitivityScale).coerceIn(-1f, 1f)
        targetY = ((orientation[1] / fullScaleAngle) * sensitivityScale).coerceIn(-1f, 1f)
    }

    /** Faster follow so the artwork tracks the phone instead of feeling sluggish. */
    fun update(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        val blend = (1f - exp(-dt / 0.045f)).coerceIn(0f, 1f)
        x += (targetX - x) * blend
        y += (targetY - y) * blend
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
