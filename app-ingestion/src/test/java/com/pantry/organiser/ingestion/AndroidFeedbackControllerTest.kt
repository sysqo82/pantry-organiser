package com.pantry.organiser.ingestion

import android.content.Context
import android.os.Vibrator
import android.util.Log
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidFeedbackControllerTest {

    private val context: Context = mockk(relaxed = true)
    private val vibrator: Vibrator = mockk(relaxed = true)

    private lateinit var controller: AndroidFeedbackController

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { context.getSystemService(Context.VIBRATOR_SERVICE) } returns vibrator
        every { vibrator.hasVibrator() } returns true

        controller = AndroidFeedbackController(context)
    }

    @Test
    fun `signalSuccess emits Success effect`() = runTest {
        controller.signalSuccess()
        val effect = controller.effects.first()
        assertEquals(FeedbackEffect.Success, effect)
    }

    @Test
    fun `signalUnknown emits Unknown effect`() = runTest {
        controller.signalUnknown()
        val effect = controller.effects.first()
        assertEquals(FeedbackEffect.Unknown, effect)
    }

    @Test
    fun `signalDuplicate emits Duplicate effect`() = runTest {
        controller.signalDuplicate()
        val effect = controller.effects.first()
        assertEquals(FeedbackEffect.Duplicate, effect)
    }
}
