package com.ironmonone.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The phone's motor and sensors as a game sees them.
 *
 * Rumble: libretro gives two motor strengths, 0..1. The phone has one motor
 * (usually), so the louder of the two drives it: above zero buzzes at that
 * amplitude for as long as the core keeps it there (a long one-shot that is
 * re-armed on every change), zero cancels.
 *
 * Sensors: libretro wants the accelerometer in g, the gyroscope in rad/s
 * and illuminance in lux. Android gives m/s^2, rad/s and lux, so only the
 * accelerometer is scaled. [normaliseAccel] is the pure part, so a test can
 * pin it.
 */
object PhoneHardware {

    fun vibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    }.getOrNull()?.takeIf { it.hasVibrator() }

    /** 0..255 amplitude from the two libretro strengths; 0 means stop. */
    fun amplitude(weak: Float, strong: Float): Int =
        (maxOf(weak, strong).coerceIn(0f, 1f) * 255f).toInt()

    fun rumble(vib: Vibrator, weak: Float, strong: Float) {
        val amp = amplitude(weak, strong)
        runCatching {
            if (amp <= 0) vib.cancel()
            else if (vib.hasAmplitudeControl()) vib.vibrate(VibrationEffect.createOneShot(2000, amp))
            else vib.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    const val G = 9.80665f

    /** m/s^2 to g, the unit mGBA reads for tilt. */
    fun normaliseAccel(v: Float): Float = v / G

    /**
     * Registers only the sensors the core asked for (mask: 1 accel, 2 gyro,
     * 4 illuminance) and unregisters the rest. Every reading pushes the
     * whole set through [sink].
     */
    class SensorFeed(context: Context, private val sink: (Float, Float, Float, Float, Float, Float, Float) -> Unit) {
        private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        private var mask = 0
        private val v = FloatArray(7)
        private val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> { v[0] = normaliseAccel(e.values[0]); v[1] = normaliseAccel(e.values[1]); v[2] = normaliseAccel(e.values[2]) }
                    Sensor.TYPE_GYROSCOPE -> { v[3] = e.values[0]; v[4] = e.values[1]; v[5] = e.values[2] }
                    Sensor.TYPE_LIGHT -> v[6] = e.values[0]
                }
                sink(v[0], v[1], v[2], v[3], v[4], v[5], v[6])
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        fun want(newMask: Int) {
            if (newMask == mask || sm == null) return
            sm.unregisterListener(listener)
            mask = newMask
            fun reg(type: Int) { sm.getDefaultSensor(type)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) } }
            if (mask and 1 != 0) reg(Sensor.TYPE_ACCELEROMETER)
            if (mask and 2 != 0) reg(Sensor.TYPE_GYROSCOPE)
            if (mask and 4 != 0) reg(Sensor.TYPE_LIGHT)
        }

        fun stop() { sm?.unregisterListener(listener); mask = 0 }
    }
}
