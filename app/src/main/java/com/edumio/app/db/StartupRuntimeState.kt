package com.edumio.app.db

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single authoritative startup gate: UI must not show pool/audit counts until [phase] is [StartupPhase.Ready].
 *
 * QuizActivity and other quiz-building screens must await [phase] reaching [StartupPhase.Ready]
 * before querying the question pool. Without this gate, quiz builders can run against
 * an empty/partial DB during first-install seeding and show "Soru havuzu yetersiz".
 */
object StartupRuntimeState {

    sealed class StartupPhase {
        data object Initializing : StartupPhase()
        data class Ready(val payload: StartupAuditRecorder.StartupReadyPayload) : StartupPhase()
    }

    private val _phase = MutableStateFlow<StartupPhase>(StartupPhase.Initializing)
    val phase: StateFlow<StartupPhase> = _phase.asStateFlow()

    /** True once seed + integrity check + audit have completed. */
    val startupInitializationComplete: Boolean
        get() = _phase.value is StartupPhase.Ready

    /** Epoch millis when [publishReady] was called, or 0 if still initializing. */
    @Volatile
    var readyTimestampMs: Long = 0L
        private set

    fun markInitializing() {
        _phase.value = StartupPhase.Initializing
        readyTimestampMs = 0L
    }

    fun publishReady(payload: StartupAuditRecorder.StartupReadyPayload) {
        readyTimestampMs = System.currentTimeMillis()
        _phase.value = StartupPhase.Ready(payload)
    }
}
