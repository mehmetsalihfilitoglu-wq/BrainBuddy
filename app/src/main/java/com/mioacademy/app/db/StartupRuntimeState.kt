package com.mioacademy.app.db

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single authoritative startup gate: UI must not show pool/audit counts until [phase] is [StartupPhase.Ready].
 */
object StartupRuntimeState {

    sealed class StartupPhase {
        data object Initializing : StartupPhase()
        data class Ready(val payload: StartupAuditRecorder.StartupReadyPayload) : StartupPhase()
    }

    private val _phase = MutableStateFlow<StartupPhase>(StartupPhase.Initializing)
    val phase: StateFlow<StartupPhase> = _phase.asStateFlow()

    val startupInitializationComplete: Boolean
        get() = _phase.value is StartupPhase.Ready

    fun markInitializing() {
        _phase.value = StartupPhase.Initializing
    }

    fun publishReady(payload: StartupAuditRecorder.StartupReadyPayload) {
        _phase.value = StartupPhase.Ready(payload)
    }
}
