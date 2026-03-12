package com.example.nanobot.core.web

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WebDiagnosticsSnapshot(
    val requestKind: String,
    val target: String,
    val endpoint: String? = null,
    val proxyConfigured: Boolean,
    val proxyValue: String? = null,
    val dnsResolutionSkipped: Boolean,
    val allowlistedHosts: List<String> = emptyList()
)

@Singleton
class WebDiagnosticsStore @Inject constructor() {
    private val snapshotState = MutableStateFlow<WebDiagnosticsSnapshot?>(null)
    val snapshot: StateFlow<WebDiagnosticsSnapshot?> = snapshotState.asStateFlow()

    fun publish(snapshot: WebDiagnosticsSnapshot) {
        snapshotState.value = snapshot
    }
}
