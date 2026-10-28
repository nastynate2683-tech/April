package com.example.motogyro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.PI
import kotlin.math.exp

/** Rotation-vector motion input with an ultra-responsive home-screen response. */
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
        // Keep the existing 0-100 control, but make the full range strongly responsive.
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

        // Ultra-sensitive angular window: small normal phone tilts now drive a large
        // back-and-forth range across the six depth planes.
        val fullScaleAngle = PI.toFloat() / 22.5f // 8 degrees
        targetX = ((orientation[2] / fullScaleAngle) * sensitivityScale).coerceIn(-1f, 1f)
        targetY = ((orientation[1] / fullScaleAngle) * sensitivityScale).coerceIn(-1f, 1f)
    }

    /** Near-instant tracking so the artwork follows phone movement without sluggish lag. */
    fun update(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        val blend = (1f - exp(-dt / 0.022f)).coerceIn(0f, 1f)
        x += (targetX - x) * blend
        y += (targetY - y) * blend
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
