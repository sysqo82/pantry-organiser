package com.pantry.organiser.dashboard.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Window
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Manages screen brightness, keep-screen-on flags, idle dimming, and night mode based on
 * hardware sensor readings (ambient light, accelerometer, & gyroscope) for a tablet mounted
 * in landscape on a pantry door.
 */
class PantryDoorSensorManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : SensorEventListener {

    enum class DoorState {
        CLOSED,
        OPEN,
        IDLE_OPEN
    }

    companion object {
        private const val TAG = "PantryDoorSensor"

        const val LIGHT_OPEN_THRESHOLD = 10.0f
        const val LIGHT_CLOSED_THRESHOLD = 2.0f

        const val GYRO_ROTATION_THRESHOLD = 0.25f // rad/s angular speed
        const val MOTION_PER_AXIS_DELTA_THRESHOLD = 1.2f // m/s² axis shift

        const val IDLE_TIMEOUT_DAY_MS = 3 * 60 * 1000L // 3 minutes during day
        const val IDLE_TIMEOUT_NIGHT_MS = 60 * 1000L // 1 minute during night
        const val SECONDARY_IDLE_CLOSED_MS = 60 * 1000L // 1 minute in IDLE before dark

        const val BRIGHTNESS_FULL = 1.0f
        const val BRIGHTNESS_NIGHT = 0.15f
        const val BRIGHTNESS_DIM = 0.05f
        const val BRIGHTNESS_DARK = 0.00f
    }

    var isNightModeEnabled: Boolean = true
    var nightStartHour: Int = 23 // 11 PM
    var nightEndHour: Int = 6   // 6 AM

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var activeWindow: Window? = null

    private val _doorState = MutableStateFlow(DoorState.CLOSED)
    val doorState: StateFlow<DoorState> = _doorState.asStateFlow()

    private var idleJob: Job? = null
    private var userBoostedNightBrightness = false

    private var lastAccelX: Float? = null
    private var lastAccelY: Float? = null
    private var lastAccelZ: Float? = null

    fun attachWindow(window: Window) {
        this.activeWindow = window
        Log.d(TAG, "Window attached. Initial state: ${_doorState.value}")
        applyStateToWindow(_doorState.value)
    }

    fun startListening() {
        if (sensorManager == null) {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        }

        val manager = sensorManager
        if (manager == null) {
            Log.e(TAG, "SensorManager service not available on device!")
            return
        }

        lightSensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        Log.i(
            TAG,
            "Sensors available -> Light: ${lightSensor != null}, Accel: ${accelerometer != null}, Gyro: ${gyroscope != null}"
        )

        lightSensor?.let {
            val registered = manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "Light sensor listener registered: $registered")
        } ?: Log.w(TAG, "Light sensor NOT found on this device! Utilizing motion and idle timer auto-sleep.")

        accelerometer?.let {
            val registered = manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Accelerometer listener registered: $registered")
        } ?: Log.w(TAG, "Accelerometer NOT found on this device!")

        gyroscope?.let {
            val registered = manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Gyroscope listener registered: $registered")
        } ?: Log.w(TAG, "Gyroscope NOT found on this device!")
    }

    fun stopListening() {
        Log.i(TAG, "Stopping sensor listeners")
        sensorManager?.unregisterListener(this)
        cancelIdleTimer()
    }

    fun onUserInteraction() {
        Log.d(TAG, "User touched screen. Current state: ${_doorState.value}")
        if (_doorState.value != DoorState.CLOSED) {
            if (isNightModeEnabled && isNightTime()) {
                userBoostedNightBrightness = true
                Log.d(TAG, "Night mode user boost activated (100% brightness)")
            }
            setDoorState(DoorState.OPEN)
            resetIdleTimer()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                handleLightSensor(lux)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                handleAccelerometer(x, y, z)
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                handleGyroscope(gx, gy, gz)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name} -> accuracy=$accuracy")
    }

    fun isNightTime(calendar: Calendar = Calendar.getInstance()): Boolean {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return if (nightStartHour > nightEndHour) {
            hour >= nightStartHour || hour < nightEndHour
        } else {
            hour in nightStartHour until nightEndHour
        }
    }

    private fun handleLightSensor(lux: Float) {
        if (lux >= LIGHT_OPEN_THRESHOLD) {
            if (_doorState.value == DoorState.CLOSED) {
                Log.i(TAG, "Door OPEN triggered by light sensor (lux=$lux >= $LIGHT_OPEN_THRESHOLD)")
                setDoorState(DoorState.OPEN)
                resetIdleTimer()
            }
        } else if (lux <= LIGHT_CLOSED_THRESHOLD) {
            if (_doorState.value != DoorState.CLOSED) {
                Log.i(TAG, "Door CLOSED triggered by light sensor (lux=$lux <= $LIGHT_CLOSED_THRESHOLD)")
                setDoorState(DoorState.CLOSED)
                cancelIdleTimer()
            }
        }
    }

    private fun handleAccelerometer(x: Float, y: Float, z: Float) {
        val prevX = lastAccelX
        val prevY = lastAccelY
        val prevZ = lastAccelZ

        lastAccelX = x
        lastAccelY = y
        lastAccelZ = z

        if (prevX != null && prevY != null && prevZ != null) {
            val perAxisDelta = abs(x - prevX) + abs(y - prevY) + abs(z - prevZ)
            if (perAxisDelta >= MOTION_PER_AXIS_DELTA_THRESHOLD && _doorState.value != DoorState.OPEN) {
                Log.i(
                    TAG,
                    "Door OPEN triggered by Accelerometer! delta=%.2f >= threshold=%.2f (Previous state: %s)"
                        .format(perAxisDelta, MOTION_PER_AXIS_DELTA_THRESHOLD, _doorState.value)
                )
                setDoorState(DoorState.OPEN)
                resetIdleTimer()
            }
        }
    }

    private fun handleGyroscope(gx: Float, gy: Float, gz: Float) {
        val angularSpeed = sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()
        if (angularSpeed >= GYRO_ROTATION_THRESHOLD && _doorState.value != DoorState.OPEN) {
            Log.i(
                TAG,
                "Door OPEN triggered by Gyroscope! angularSpeed=%.2f >= threshold=%.2f (Previous state: %s)"
                    .format(angularSpeed, GYRO_ROTATION_THRESHOLD, _doorState.value)
            )
            setDoorState(DoorState.OPEN)
            resetIdleTimer()
        }
    }

    fun setDoorState(newState: DoorState) {
        val oldState = _doorState.value
        if (newState == DoorState.CLOSED) {
            userBoostedNightBrightness = false
        }

        if (oldState != newState) {
            Log.i(TAG, "Door state transition: $oldState -> $newState")
            _doorState.value = newState
        }
        applyStateToWindow(newState)
    }

    private fun applyStateToWindow(state: DoorState) {
        val window = activeWindow
        if (window == null) {
            Log.w(TAG, "Cannot apply state to window: activeWindow is null!")
            return
        }

        val lp = window.attributes

        when (state) {
            DoorState.OPEN -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (isNightModeEnabled && isNightTime() && !userBoostedNightBrightness) {
                    lp.screenBrightness = BRIGHTNESS_NIGHT
                } else {
                    lp.screenBrightness = BRIGHTNESS_FULL
                }
            }
            DoorState.IDLE_OPEN -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                lp.screenBrightness = BRIGHTNESS_DIM
            }
            DoorState.CLOSED -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                lp.screenBrightness = BRIGHTNESS_DARK
            }
        }

        Log.d(TAG, "Window updated -> State: $state, Brightness: ${lp.screenBrightness}")
        window.attributes = lp
    }

    private fun resetIdleTimer() {
        cancelIdleTimer()
        val timeout = if (isNightModeEnabled && isNightTime()) IDLE_TIMEOUT_NIGHT_MS else IDLE_TIMEOUT_DAY_MS
        Log.d(TAG, "Idle timer started -> timeout=${timeout / 1000}s")
        idleJob = scope.launch {
            delay(timeout)
            if (_doorState.value == DoorState.OPEN) {
                Log.i(TAG, "Idle timer expired. Transitioning OPEN -> IDLE_OPEN")
                setDoorState(DoorState.IDLE_OPEN)

                delay(SECONDARY_IDLE_CLOSED_MS)
                if (_doorState.value == DoorState.IDLE_OPEN) {
                    Log.i(TAG, "Secondary idle timer expired. Transitioning IDLE_OPEN -> CLOSED")
                    setDoorState(DoorState.CLOSED)
                }
            }
        }
    }

    private fun cancelIdleTimer() {
        if (idleJob != null) {
            Log.d(TAG, "Idle timer cancelled")
            idleJob?.cancel()
            idleJob = null
        }
    }
}
