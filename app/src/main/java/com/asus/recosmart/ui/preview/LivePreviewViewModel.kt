package com.asus.recosmart.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asus.recosmart.RecoSmartApp
import com.asus.recosmart.domain.model.CameraMode
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.repository.CameraRepository
import com.asus.recosmart.domain.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LivePreviewViewModel(
    private val repository: CameraRepository = RecoSmartApp.instance.cameraRepository
) : ViewModel() {

    val cameraStatus: StateFlow<CameraStatus> = repository.cameraStatus
    val sessionState: StateFlow<SessionState> = repository.sessionState
    val isMockMode: StateFlow<Boolean> = repository.isMockMode

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    fun logRtsp(message: String) {
        repository.logRtsp(message)
    }

    fun setFirstVideoFrameRendered(rendered: Boolean) {
        repository.setFirstVideoFrameRendered(rendered)
        if (rendered) {
            _statusText.value = "Canlı görüntü aktif."
        }
    }

    fun prepareLiveView(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (isMockMode.value) {
                onResult(true, null)
                return@launch
            }
            _statusText.value = "Viewfinder hazırlanıyor (RESET_TO_VF)..."
            val res = repository.prepareLiveView()
            if (res.isSuccess && res.getOrNull()?.isSuccess == true) {
                _statusText.value = "Viewfinder hazır."
                onResult(true, null)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage
                    ?: "RESET_TO_VF reddedildi (rval=${res.getOrNull()?.rval})"
                _statusText.value = "Viewfinder hazırlanamadı: $err"
                repository.logRtsp("[VF ERROR] $err")
                onResult(false, err)
            }
        }
    }

    fun stopLiveView() {
        viewModelScope.launch {
            if (!isMockMode.value && sessionState.value is SessionState.Connected) {
                repository.stopLiveView()
            }
        }
    }

    private var isActionPending = false

    fun toggleRecording() {
        if (isActionPending) return
        isActionPending = true
        viewModelScope.launch {
            try {
                val isRecording = cameraStatus.value.isRecording
                if (isRecording) {
                    _statusText.value = "Kayıt durduruluyor..."
                    val res = repository.stopRecording()
                    _statusText.value = if (res.isSuccess) "Kayıt durduruldu." else "Kayıt durdurulamadı."
                } else {
                    _statusText.value = "Kayıt başlatılıyor..."
                    val res = repository.startRecording()
                    _statusText.value = if (res.isSuccess) "Kayıt başladı." else "Kayıt başlatılamadı."
                }
            } finally {
                isActionPending = false
            }
        }
    }

    fun takePhoto() {
        if (isActionPending) return
        isActionPending = true
        viewModelScope.launch {
            try {
                val isRecording = cameraStatus.value.isRecording
                if (isRecording) {
                    _statusText.value = "Kayıt sırasında fotoğraf çekiliyor (PHOTO_PIV)..."
                    val res = repository.takePhotoPiv()
                    _statusText.value = if (res.isSuccess) "Kayıt sırasında fotoğraf çekildi." else "Fotoğraf çekilemedi."
                } else {
                    _statusText.value = "Fotoğraf çekiliyor..."
                    val res = repository.takePhoto()
                    _statusText.value = if (res.isSuccess) "Fotoğraf çekildi." else "Fotoğraf çekilemedi."
                }
            } finally {
                isActionPending = false
            }
        }
    }
}
