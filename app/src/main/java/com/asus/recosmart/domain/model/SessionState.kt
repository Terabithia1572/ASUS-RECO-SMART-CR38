package com.asus.recosmart.domain.model

sealed interface SessionState {
    object Disconnected : SessionState
    object Connecting : SessionState
    object WifiNotConnected : SessionState
    object CameraNetworkNotFound : SessionState
    object RouteNotAvailable : SessionState
    object TcpConnecting : SessionState
    data class TcpConnectionFailed(val reason: String) : SessionState
    object TcpConnected : SessionState
    object SessionStarting : SessionState
    object StartSessionSent : SessionState
    object StartSessionTimeout : SessionState
    object TokenInvalid : SessionState
    data class DataSocketFailed(val reason: String) : SessionState
    data class Connected(val token: Int) : SessionState
    data class Error(val message: String) : SessionState

    val isConnected: Boolean
        get() = this is Connected
}
