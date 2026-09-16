package com.asus.recosmart.ui.files

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asus.recosmart.RecoSmartApp
import com.asus.recosmart.data.network.MediaDownloader
import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FileFilterCategory(val label: String) {
    ALL("Tümü"),
    VIDEOS("Videolar"),
    PHOTOS("Fotoğraflar"),
    EMERGENCY("Acil Durum"),
    DOWNLOADED("Telefona İndirilenler")
}

data class FileExportProgress(
    val progressPercent: Int = 0,
    val isDownloading: Boolean = false,
    val savedUri: Uri? = null,
    val error: String? = null
)

class FileManagerViewModel(
    private val repository: CameraRepository = RecoSmartApp.instance.cameraRepository
) : ViewModel() {

    private val _rawFiles = MutableStateFlow<List<CameraFile>>(emptyList())

    private val _selectedCategory = MutableStateFlow(FileFilterCategory.ALL)
    val selectedCategory: StateFlow<FileFilterCategory> = _selectedCategory.asStateFlow()

    private val _filteredFiles = MutableStateFlow<List<CameraFile>>(emptyList())
    val files: StateFlow<List<CameraFile>> = _filteredFiles.asStateFlow()

    private val _exportProgressMap = MutableStateFlow<Map<String, FileExportProgress>>(emptyMap())
    val exportProgressMap: StateFlow<Map<String, FileExportProgress>> = _exportProgressMap.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        loadFiles()
    }

    fun setCategory(category: FileFilterCategory) {
        _selectedCategory.value = category
        applyFilter()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Kamera dizinleri taranıyor..."
            val result = repository.listFiles(CameraStatus.DEFAULT_DCIM_PATH)
            if (result.isSuccess) {
                _rawFiles.value = result.getOrDefault(emptyList())
                applyFilter()
                _statusMessage.value = "${_rawFiles.value.size} medya kaydı tarandı."
            } else {
                _statusMessage.value = "DCIM dizinleri okunamadı: ${result.exceptionOrNull()?.localizedMessage}"
            }
            _isLoading.value = false
        }
    }

    fun downloadToPhone(context: Context, file: CameraFile, onComplete: ((Uri) -> Unit)? = null) {
        val key = file.filename
        val currentProgress = _exportProgressMap.value[key]
        if (currentProgress?.isDownloading == true) return

        viewModelScope.launch {
            updateExportProgress(key, FileExportProgress(progressPercent = 0, isDownloading = true))
            val label = if (file.isVideo) "Video" else "Fotoğraf"
            _statusMessage.value = "$label indiriliyor (%0)..."

            val result = MediaDownloader.downloadToMediaStore(context, file) { percent ->
                updateExportProgress(key, FileExportProgress(progressPercent = percent, isDownloading = true))
                _statusMessage.value = "$label indiriliyor (%$percent)..."
            }

            if (result.isSuccess) {
                val uri = result.getOrThrow()
                updateExportProgress(key, FileExportProgress(progressPercent = 100, isDownloading = false, savedUri = uri))
                applyFilter()
                _statusMessage.value = "$label telefona kaydedildi!"
                onComplete?.invoke(uri)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "İndirme başarısız"
                updateExportProgress(key, FileExportProgress(isDownloading = false, error = err))
                _statusMessage.value = "$label indirilemedi: $err"
            }
        }
    }

    fun openInExternalApp(context: Context, file: CameraFile) {
        viewModelScope.launch {
            val label = if (file.isVideo) "Video" else "Fotoğraf"
            val key = file.filename
            val existingUri = _exportProgressMap.value[key]?.savedUri

            if (existingUri != null) {
                MediaDownloader.openInExternalApp(context, existingUri, file.isVideo)
                return@launch
            }

            _statusMessage.value = "$label dış uygulamada açılmak için hazırlanıyor..."
            updateExportProgress(key, FileExportProgress(progressPercent = 0, isDownloading = true))

            val cacheResult = MediaDownloader.getOrCacheFile(context, file) { percent ->
                updateExportProgress(key, FileExportProgress(progressPercent = percent, isDownloading = true))
            }

            updateExportProgress(key, FileExportProgress(isDownloading = false))

            if (cacheResult.isSuccess) {
                val contentUri = cacheResult.getOrThrow()
                val openRes = MediaDownloader.openInExternalApp(context, contentUri, file.isVideo)
                if (openRes.isFailure) {
                    _statusMessage.value = "Bu dosyayı açabilecek bir uygulama bulunamadı."
                } else {
                    _statusMessage.value = "$label dış uygulamada açıldı."
                }
            } else {
                _statusMessage.value = "$label hazırlanamadı: ${cacheResult.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun shareMedia(context: Context, file: CameraFile) {
        viewModelScope.launch {
            val label = if (file.isVideo) "Video" else "Fotoğraf"
            val key = file.filename
            val existingUri = _exportProgressMap.value[key]?.savedUri

            if (existingUri != null) {
                MediaDownloader.shareMedia(context, existingUri, file.isVideo)
                return@launch
            }

            _statusMessage.value = "$label paylaşılmak için hazırlanıyor..."
            updateExportProgress(key, FileExportProgress(progressPercent = 0, isDownloading = true))

            val cacheResult = MediaDownloader.getOrCacheFile(context, file) { percent ->
                updateExportProgress(key, FileExportProgress(progressPercent = percent, isDownloading = true))
            }

            updateExportProgress(key, FileExportProgress(isDownloading = false))

            if (cacheResult.isSuccess) {
                val contentUri = cacheResult.getOrThrow()
                MediaDownloader.shareMedia(context, contentUri, file.isVideo)
                _statusMessage.value = "$label paylaşım menüsü açıldı."
            } else {
                _statusMessage.value = "$label hazırlanamadı: ${cacheResult.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteFile(file: CameraFile) {
        viewModelScope.launch {
            _statusMessage.value = "${file.filename} siliniyor..."
            val result = repository.deleteFile(file.fullCameraPath)
            if (result.isSuccess) {
                _statusMessage.value = "${file.filename} başarıyla silindi."
                loadFiles()
            } else {
                _statusMessage.value = "${file.filename} silinemedi: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    private fun applyFilter() {
        val raw = _rawFiles.value
        _filteredFiles.value = when (_selectedCategory.value) {
            FileFilterCategory.ALL -> raw
            FileFilterCategory.VIDEOS -> raw.filter { it.isVideo && !it.isEmergency }
            FileFilterCategory.PHOTOS -> raw.filter { it.isPhoto }
            FileFilterCategory.EMERGENCY -> raw.filter { it.isEmergency }
            FileFilterCategory.DOWNLOADED -> raw.filter { _exportProgressMap.value[it.filename]?.savedUri != null }
        }
    }

    private fun updateExportProgress(filename: String, progress: FileExportProgress) {
        val current = _exportProgressMap.value.toMutableMap()
        current[filename] = progress
        _exportProgressMap.value = current
    }
}
