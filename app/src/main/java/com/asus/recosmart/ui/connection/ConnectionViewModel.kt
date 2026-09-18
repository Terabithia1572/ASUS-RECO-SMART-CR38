package com.asus.recosmart.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asus.recosmart.RecoSmartApp
import com.asus.recosmart.data.preferences.UserPreferencesManager
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.model.DeviceStatus
import com.asus.recosmart.domain.model.SessionState
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val repository: CameraRepository = RecoSmartApp.instance.cameraRepository,
    private val preferencesManager: UserPreferencesManager = RecoSmartApp.instance.userPreferencesManager
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = repository.sessionState
    val cameraStatus: StateFlow<CameraStatus> = repository.cameraStatus
    val isMockMode: StateFlow<Boolean> = repository.isMockMode

    val isVehicleModeEnabled: StateFlow<Boolean> = preferencesManager.isVehicleModeEnabled
    val autoStartRecordingIfIdle: StateFlow<Boolean> = preferencesManager.autoStartRecordingIfIdle

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
        _statusMessage.value = if (enabled) "Simülasyon Moduna Geçildi" else "Gerçek Kamera Moduna Geçildi"
    }

    fun setVehicleMode(enabled: Boolean) {
        preferencesManager.setVehicleModeEnabled(enabled)
    }

    fun setAutoStartRecording(autoStart: Boolean) {
        preferencesManager.setAutoStartRecordingIfIdle(autoStart)
    }

    fun connect() {
        viewModelScope.launch {
            _statusMessage.value = null
            val portInt = _commandPort.value.toIntOrNull() ?: CameraStatus.DEFAULT_COMMAND_PORT
            val result = repository.connect(_cameraIp.value, portInt)
            if (result.isSuccess) {
                if (isVehicleModeEnabled.value) {
                    executeVehicleModeAutomation()
                }
            } else {
                _statusMessage.value = "Bağlantı hatası: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    private suspend fun executeVehicleModeAutomation() {
        val statusRes = repository.getAppStatus()
        val deviceStatus = statusRes.getOrNull() ?: DeviceStatus.UNKNOWN
        if (deviceStatus == DeviceStatus.RECORD || repository.cameraStatus.value.isRecording) {
            _statusMessage.value = "Araç Modu: Kamera zaten kayıt yapıyor."
        } else if ((deviceStatus == DeviceStatus.VF || deviceStatus == DeviceStatus.IDLE) && autoStartRecordingIfIdle.value) {
            val recRes = repository.startRecording()
            if (recRes.isSuccess) {
                _statusMessage.value = "Araç Modu: Otomatik kayıt başlatıldı."
            } else {
                _statusMessage.value = "Araç Modu: Otomatik kayıt başlatılamadı."
            }
        } else {
            _statusMessage.value = "Araç Modu: Oturum hazır (${deviceStatus.name})."
        }
    }

    private val _diagnosticResult = MutableStateFlow<com.asus.recosmart.domain.model.ConnectionDiagnosticResult?>(null)
    val diagnosticResult: StateFlow<com.asus.recosmart.domain.model.ConnectionDiagnosticResult?> = _diagnosticResult.asStateFlow()

    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()

    fun runDiagnosticTest() {
        viewModelScope.launch {
            _isDiagnosing.value = true
            _diagnosticResult.value = null
            val result = repository.performConnectionDiagnostic()
            _diagnosticResult.value = result
            _isDiagnosing.value = false
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            _statusMessage.value = "Bağlantı Kesildi"
        }
    }
}

