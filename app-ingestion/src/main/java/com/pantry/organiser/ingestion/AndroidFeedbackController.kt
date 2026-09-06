package com.pantry.organiser.ingestion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFeedbackController @Inject constructor(
    @ApplicationContext private val context: Context
) : FeedbackController {

    private val _effects = MutableSharedFlow<FeedbackEffect>(replay = 1, extraBufferCapacity = 1)
    override val effects: SharedFlow<FeedbackEffect> = _effects.asSharedFlow()

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.e("FeedbackController", "Failed to acquire Vibrator service: ${e.message}")
            null
        }
    }

    override fun signalSuccess() {
        // Visual green flash only, no haptic vibration for successful scan
        _effects.tryEmit(FeedbackEffect.Success)
    }

    override fun signalUnknown() {
        vibrate(longArrayOf(0, 150, 100, 150))
        _effects.tryEmit(FeedbackEffect.Unknown)
    }

    override fun signalDuplicate() {
        vibrate(longArrayOf(0, 100, 100, 100))
        _effects.tryEmit(FeedbackEffect.Duplicate)
    }

    private fun vibrate(pattern: LongArray) {
        val v = vibrator
        if (v == null || !v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e("FeedbackController", "Vibration failed: ${e.message}", e)
        }
    }
}
