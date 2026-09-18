package com.asus.recosmart.data.network

import com.asus.recosmart.domain.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    val activeToken: Int
        get() = when (val state = _sessionState.value) {
            is SessionState.Connected -> state.token
            else -> 0
        }

    val isConnected: Boolean
        get() = _sessionState.value is SessionState.Connected

    fun setState(state: SessionState) {
        _sessionState.value = state
    }

    fun setTcpConnecting() {
        _sessionState.value = SessionState.TcpConnecting
    }

    fun setTcpConnected() {
        _sessionState.value = SessionState.TcpConnected
    }

    fun setTcpConnectionFailed(reason: String) {
        _sessionState.value = SessionState.TcpConnectionFailed(reason)
    }

    fun setSessionStarting() {
        _sessionState.value = SessionState.SessionStarting
    }

    fun setStartSessionSent() {
        _sessionState.value = SessionState.StartSessionSent
    }

    fun setConnected(token: Int) {
        _sessionState.value = SessionState.Connected(token)
    }

    fun setDisconnected() {
        _sessionState.value = SessionState.Disconnected
    }

    fun setError(message: String) {
        _sessionState.value = SessionState.Error(message)
    }
}
