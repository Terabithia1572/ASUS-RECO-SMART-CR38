package com.asus.recosmart.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asus.recosmart.RecoSmartApp
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.model.SessionState
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val repository: CameraRepository = RecoSmartApp.instance.cameraRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = repository.sessionState
    val cameraStatus: StateFlow<CameraStatus> = repository.cameraStatus
    val isMockMode: StateFlow<Boolean> = repository.isMockMode

    private val _cameraIp = MutableStateFlow(CameraStatus.DEFAULT_CAMERA_IP)
    val cameraIp: StateFlow<String> = _cameraIp.asStateFlow()

    private val _commandPort = MutableStateFlow(CameraStatus.DEFAULT_COMMAND_PORT.toString())
    val commandPort: StateFlow<String> = _commandPort.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun onIpChanged(newIp: String) {
        _cameraIp.value = newIp
    }

    fun onPortChanged(newPort: String) {
        _commandPort.value = newPort
    }

    fun toggleMockMode(enabled: Boolean) {
        repository.toggleMockMode(enabled)
        _statusMessage.value = if (enabled) "Switched to Mock Debug Mode" else "Switched to Physical Camera Mode"
    }

    fun connect() {
        viewModelScope.launch {
            _statusMessage.value = null
            val portInt = _commandPort.value.toIntOrNull() ?: CameraStatus.DEFAULT_COMMAND_PORT
            val result = repository.connect(_cameraIp.value, portInt)
            if (result.isFailure) {
                _statusMessage.value = "Connection failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            _statusMessage.value = "Disconnected"
        }
    }
}
