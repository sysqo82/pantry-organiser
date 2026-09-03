package com.pantry.organiser.ingestion

import kotlinx.coroutines.flow.Flow

sealed class FeedbackEffect {
    object Success : FeedbackEffect()
    object Unknown : FeedbackEffect()
    object Duplicate : FeedbackEffect()
}

interface FeedbackController {
    val effects: Flow<FeedbackEffect>
    fun signalSuccess()
    fun signalUnknown()
    fun signalDuplicate()
}
