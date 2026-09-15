package com.asus.recosmart.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asus.recosmart.RecoSmartApp
import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileManagerViewModel(
    private val repository: CameraRepository = RecoSmartApp.instance.cameraRepository
) : ViewModel() {

    private val _files = MutableStateFlow<List<CameraFile>>(emptyList())
    val files: StateFlow<List<CameraFile>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Kamera dizini taranıyor..."
            val result = repository.listFiles(CameraStatus.DEFAULT_DCIM_PATH)
            if (result.isSuccess) {
                _files.value = result.getOrDefault(emptyList())
                _statusMessage.value = "${_files.value.size} kayıt yüklendi."
            } else {
                _statusMessage.value = "DCIM dizini okunamadı."
            }
            _isLoading.value = false
        }
    }

    fun deleteFile(file: CameraFile) {
        viewModelScope.launch {
            _statusMessage.value = "${file.filename} siliniyor..."
            val result = repository.deleteFile(file.fullCameraPath)
            if (result.isSuccess) {
                _statusMessage.value = "${file.filename} silindi."
                loadFiles()
            } else {
                _statusMessage.value = "${file.filename} silinemedi."
            }
        }
    }

    fun formatSdCard() {
        viewModelScope.launch {
            _statusMessage.value = "Formatting SD Card..."
            val result = repository.formatSdCard()
            if (result.isSuccess) {
                _statusMessage.value = "SD Card formatted successfully!"
                loadFiles()
            } else {
                _statusMessage.value = "SD Card format failed"
            }
        }
    }
}
