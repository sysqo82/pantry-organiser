package com.pantry.organiser.dashboard.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.util.Log
import android.view.Window
import android.view.WindowManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class PantryDoorSensorManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val window = mockk<Window>(relaxed = true)
    private val layoutParams = WindowManager.LayoutParams()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var sensorManager: PantryDoorSensorManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        every { window.attributes } returns layoutParams

        sensorManager = PantryDoorSensorManager(context, testScope)
        sensorManager.isNightModeEnabled = false // default to day mode for base tests
        sensorManager.attachWindow(window)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is CLOSED and window brightness is DARK`() {
        assertEquals(PantryDoorSensorManager.DoorState.CLOSED, sensorManager.doorState.value)
        assertEquals(PantryDoorSensorManager.BRIGHTNESS_DARK, layoutParams.screenBrightness, 0.001f)
        verify { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test
    fun `setDoorState OPEN sets full brightness and keep screen on flag`() {
        sensorManager.setDoorState(PantryDoorSensorManager.DoorState.OPEN)

        assertEquals(PantryDoorSensorManager.DoorState.OPEN, sensorManager.doorState.value)
        assertEquals(PantryDoorSensorManager.BRIGHTNESS_FULL, layoutParams.screenBrightness, 0.001f)
        verify { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test
    fun `setDoorState IDLE_OPEN sets dim brightness and keep screen on flag`() {
        sensorManager.setDoorState(PantryDoorSensorManager.DoorState.IDLE_OPEN)

        assertEquals(PantryDoorSensorManager.DoorState.IDLE_OPEN, sensorManager.doorState.value)
        assertEquals(PantryDoorSensorManager.BRIGHTNESS_DIM, layoutParams.screenBrightness, 0.001f)
        verify { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test
    fun `light sensor event above threshold opens door`() {
        val lightSensor = mockk<Sensor>()
        every { lightSensor.type } returns Sensor.TYPE_LIGHT

        val event = mockk<SensorEvent>()
        event.sensor = lightSensor
        val valuesField = SensorEvent::class.java.getField("values")
        valuesField.isAccessible = true
        valuesField.set(event, floatArrayOf(15.0f))

        sensorManager.onSensorChanged(event)

        assertEquals(PantryDoorSensorManager.DoorState.OPEN, sensorManager.doorState.value)
        assertEquals(PantryDoorSensorManager.BRIGHTNESS_FULL, layoutParams.screenBrightness, 0.001f)
    }

    @Test
    fun `gyroscope rotation above threshold opens door`() {
        val gyroSensor = mockk<Sensor>()
        every { gyroSensor.type } returns Sensor.TYPE_GYROSCOPE

        val event = mockk<SensorEvent>()
        event.sensor = gyroSensor
        val valuesField = SensorEvent::class.java.getField("values")
        valuesField.isAccessible = true
        valuesField.set(event, floatArrayOf(0.20f, 0.20f, 0.10f)) // angularSpeed = sqrt(0.04+0.04+0.01) = 0.30 >= 0.25

        sensorManager.onSensorChanged(event)

        assertEquals(PantryDoorSensorManager.DoorState.OPEN, sensorManager.doorState.value)
        assertEquals(PantryDoorSensorManager.BRIGHTNESS_FULL, layoutParams.screenBrightness, 0.001f)
    }

    @Test
    fun `accelerometer per-axis delta above threshold opens door`() {
        val accelSensor = mockk<Sensor>()
        every { accelSensor.type } returns Sensor.TYPE_ACCELEROMETER

        val valuesField = SensorEvent::class.java.getField("values")
        valuesField.isAccessible = true

        val event1 = mockk<SensorEvent>()
        event1.sensor = accelSensor
        valuesField.set(event1, floatArrayOf(0.0f, 9.8f, 0.0f))
        sensorManager.onSensorChanged(event1)

        val event2 = mockk<SensorEvent>()
        event2.sensor = accelSensor
        valuesField.set(event2, floatArrayOf(1.0f, 9.0f, 0.5f)) // delta = |1|+|-0.8|+|0.5| = 2.3 >= 1.2
        sensorManager.onSensorChanged(event2)

        assertEquals(PantryDoorSensorManager.DoorState.OPEN, sensorManager.doorState.value)
    }

    @Test
    fun `isNightTime correctly identifies quiet hours`() {
        sensorManager.nightStartHour = 23
        sensorManager.nightEndHour = 6

        val midnightCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 2) }
        val noonCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12) }

        assertTrue(sensorManager.isNightTime(midnightCal))
        assertFalse(sensorManager.isNightTime(noonCal))
    }

    @Test
    fun `night mode uses night brightness until user interacts`() {
        sensorManager.isNightModeEnabled = true
        sensorManager.nightStartHour = 0
        sensorManager.nightEndHour = 24 // force night time

        sensorManager.setDoorState(PantryDoorSensorManager.DoorState.OPEN)

        assertEquals(PantryDoorSensorManager.BRIGHTNESS_NIGHT, layoutParams.screenBrightness, 0.001f)

        // User taps screen
        sensorManager.onUserInteraction()

        assertEquals(PantryDoorSensorManager.BRIGHTNESS_FULL, layoutParams.screenBrightness, 0.001f)
    }

    @Test
    fun `idle timeout transitions OPEN state to IDLE_OPEN`() {
        sensorManager.setDoorState(PantryDoorSensorManager.DoorState.OPEN)
        sensorManager.onUserInteraction()

        assertEquals(PantryDoorSensorManager.DoorState.OPEN, sensorManager.doorState.value)

        testDispatcher.scheduler.advanceTimeBy(PantryDoorSensorManager.IDLE_TIMEOUT_DAY_MS + 1000L)

        assertEquals(PantryDoorSensorManager.DoorState.IDLE_OPEN, sensorManager.doorState.value)
        assertEquals(PantryDoorSensorManager.BRIGHTNESS_DIM, layoutParams.screenBrightness, 0.001f)
    }
}
