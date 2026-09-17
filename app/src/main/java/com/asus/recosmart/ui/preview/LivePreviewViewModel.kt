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

    private var prepareLiveViewJob: kotlinx.coroutines.Job? = null

    fun prepareLiveView(onResult: (Boolean, String?) -> Unit) {
        if (prepareLiveViewJob?.isActive == true) {
            repository.logRtsp("[VF INIT] Viewfinder initialization already in progress. Awaiting active job...")
            viewModelScope.launch {
                prepareLiveViewJob?.join()
                onResult(true, null)
            }
            return
        }

        prepareLiveViewJob = viewModelScope.launch {
            if (isMockMode.value) {
                onResult(true, null)
                return@launch
            }

            if (cameraStatus.value.isRecording) {
                _statusText.value = "Canlı görüntüye bağlanılıyor (Kayıt Aktif)..."
                repository.logRtsp("[LIVE] screen enter | recording=true")
                repository.logRtsp("[LIVE] camera-side preparation skipped because recording is active")
                onResult(true, null)
                return@launch
            }

            if (sessionState.value !is SessionState.Connected) {
                _statusText.value = "Oturum yenileniyor..."
                repository.logRtsp("[VF INIT] Session not connected. Running 1-shot recovery...")
                repository.recoverSession()
            }

            _statusText.value = "Viewfinder hazırlanıyor (RESET_TO_VF)..."
            var res = repository.prepareLiveView("LIVE_SCREEN_ENTER")

            if (res.isFailure) {
                repository.logRtsp("[VF INIT NOTICE] Viewfinder prep failed. Running 1-shot recovery...")
                _statusText.value = "Canlı görüntü için bağlantı yenileniyor..."
                val recRes = repository.recoverSession()
                if (recRes.isSuccess) {
                    _statusText.value = "Viewfinder yeniden hazırlanıyor..."
                    res = repository.prepareLiveView("LIVE_SCREEN_ENTER")
                }
            }

            if (res.isSuccess && res.getOrNull()?.isSuccess == true) {
                _statusText.value = "Viewfinder hazır."
                onResult(true, null)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage
                    ?: "RESET_TO_VF reddedildi (rval=${res.getOrNull()?.rval})"
                _statusText.value = "Canlı görüntü hazırlanamadı: $err"
                repository.logRtsp("[VF ERROR] $err")
                onResult(false, err)
            }
        }
    }

    fun stopLiveView(origin: String = "LIVE_SCREEN_EXIT") {
        viewModelScope.launch {
            if (!isMockMode.value && sessionState.value is SessionState.Connected) {
                repository.stopLiveView(origin)
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
                    _statusText.value = "Video kaydı sonlandırılıyor..."
                    val res = repository.stopRecording("USER_RECORD_BUTTON")
                    val discovered = res.getOrNull()?.discoveredFile
                    if (discovered != null) {
                        _statusText.value = "Kayıt kaydedildi: ${discovered.folder}/${discovered.filename}"
                    } else if (res.isSuccess) {
                        _statusText.value = "Kayıt durduruldu ancak yeni video henüz bulunamadı. Kayıtları yenileyin."
                    } else {
                        _statusText.value = "Kayıt durdurulamadı."
                    }
                } else {
                    _statusText.value = "Kayıt başlatılıyor..."
                    val res = repository.startRecording("USER_RECORD_BUTTON")
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
                    _statusText.value = "Kayıt sırasında fotoğraf çekiliyor..."
                    val res = repository.takePhotoPiv()
                    val discovered = res.getOrNull()?.discoveredFile
                    if (discovered != null) {
                        _statusText.value = "Fotoğraf kaydedildi: ${discovered.folder}/${discovered.filename}"
                    } else if (res.isSuccess) {
                        _statusText.value = "Fotoğraf komutu alındı ancak yeni dosya doğrulanamadı."
                    } else {
                        _statusText.value = "Fotoğraf çekilemedi."
                    }
                } else {
                    _statusText.value = "Fotoğraf çekiliyor..."
                    val res = repository.takePhoto("USER_PHOTO_BUTTON")
                    val discovered = res.getOrNull()?.discoveredFile
                    if (discovered != null) {
                        _statusText.value = "Fotoğraf kaydedildi: ${discovered.folder}/${discovered.filename}"
                    } else if (res.isSuccess) {
                        _statusText.value = "Fotoğraf komutu alındı ancak yeni dosya doğrulanamadı."
                    } else {
                        _statusText.value = "Fotoğraf çekilemedi."
                    }
                }
            } finally {
                isActionPending = false
            }
        }
    }
}
