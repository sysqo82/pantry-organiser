package com.pantry.organiser.ingestion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    private val _effects = MutableSharedFlow<FeedbackEffect>(extraBufferCapacity = 1)
    override val effects: SharedFlow<FeedbackEffect> = _effects.asSharedFlow()

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun signalSuccess() {
        vibrate(VibrationEffect.EFFECT_HEAVY_CLICK, longArrayOf(0, 150))
        _effects.tryEmit(FeedbackEffect.Success)
    }

    override fun signalUnknown() {
        vibrate(VibrationEffect.EFFECT_DOUBLE_CLICK, longArrayOf(0, 100, 100, 100))
        _effects.tryEmit(FeedbackEffect.Unknown)
    }

    override fun signalDuplicate() {
        // No haptic as per PRD
        _effects.tryEmit(FeedbackEffect.Duplicate)
    }

    private fun vibrate(effectId: Int, fallbackPattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(fallbackPattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(fallbackPattern, -1)
            }
        } catch (e: Exception) {
            android.util.Log.e("FeedbackController", "Vibration failed", e)
        }
    }
}
