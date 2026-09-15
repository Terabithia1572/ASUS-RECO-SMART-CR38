package com.asus.recosmart.domain.model

sealed interface SessionState {
    object Disconnected : SessionState
    object Connecting : SessionState
    object TcpConnected : SessionState
    object SessionStarting : SessionState
    data class Connected(val token: Int) : SessionState
    data class Error(val message: String) : SessionState

    val isConnected: Boolean
        get() = this is Connected
}
