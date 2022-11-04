package com.assigment.exoplayertask

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

class SensorListener(var listener: onShakeListener) : SensorEventListener{
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f

    interface onShakeListener {
        fun onShake()
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Fetching x,y,z values
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        lastAcceleration = currentAcceleration

        // Getting current accelerations
        // with the help of fetched x,y,z values
        currentAcceleration = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta: Float = currentAcceleration - lastAcceleration
        acceleration = acceleration * 0.9f + delta

        // Display a Toast message if
        // acceleration value is over 12
        if (acceleration > 12) {
            listener?.onShake()
//                Toast.makeText(applicationContext, "Shake event detected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
       // not needed
    }
}