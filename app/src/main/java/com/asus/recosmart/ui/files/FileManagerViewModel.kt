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

enum class StorageFilter(val label: String) {
    ALL("Tüm Lokasyonlar"),
    ON_CAMERA("Kamerada"),
    ON_PHONE("Telefonda")
}

enum class SortMode(val label: String) {
    DATE_DESC("Tarih: Yeniden Eskiye"),
    DATE_ASC("Tarih: Eskiden Yeniye"),
    NAME_ASC("İsim: A → Z"),
    NAME_DESC("İsim: Z → A"),
    FOLDER_ASC("Klasör: A → Z")
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
    val rawFilesCount: StateFlow<Int> = MutableStateFlow(0).apply {
        viewModelScope.launch {
            _rawFiles.collect { value = it.size }
        }
    }

    private val _selectedCategory = MutableStateFlow(FileFilterCategory.ALL)
    val selectedCategory: StateFlow<FileFilterCategory> = _selectedCategory.asStateFlow()

    private val _storageFilter = MutableStateFlow(StorageFilter.ALL)
    val storageFilter: StateFlow<StorageFilter> = _storageFilter.asStateFlow()

    private val _folderFilter = MutableStateFlow("ALL")
    val folderFilter: StateFlow<String> = _folderFilter.asStateFlow()

    private val _availableFolders = MutableStateFlow<List<String>>(emptyList())
    val availableFolders: StateFlow<List<String>> = _availableFolders.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

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
        applyFilterPipeline()
    }

    fun setStorageFilter(filter: StorageFilter) {
        _storageFilter.value = filter
        applyFilterPipeline()
    }

    fun setFolderFilter(folder: String) {
        _folderFilter.value = folder
        applyFilterPipeline()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilterPipeline()
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        applyFilterPipeline()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Kamera dizinleri taranıyor..."
            val result = repository.listFiles(CameraStatus.DEFAULT_DCIM_PATH)
            if (result.isSuccess) {
                val list = result.getOrDefault(emptyList())
                _rawFiles.value = list
                _availableFolders.value = list.map { it.folder }.distinct().sorted()
                applyFilterPipeline()
                updateStatusSummary()
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
                applyFilterPipeline()
                updateStatusSummary()
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
                updateExportProgress(key, FileExportProgress(percent, isDownloading = true))
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

    private fun applyFilterPipeline() {
        var list = _rawFiles.value

        // Step 1: Category Filter
        list = when (_selectedCategory.value) {
            FileFilterCategory.ALL -> list
            FileFilterCategory.VIDEOS -> list.filter { it.isVideo && !it.isEmergency }
            FileFilterCategory.PHOTOS -> list.filter { it.isPhoto }
            FileFilterCategory.EMERGENCY -> list.filter { it.isEmergency }
            FileFilterCategory.DOWNLOADED -> list.filter { _exportProgressMap.value[it.filename]?.savedUri != null }
        }

        // Step 2: Storage Filter
        list = when (_storageFilter.value) {
            StorageFilter.ALL -> list
            StorageFilter.ON_CAMERA -> list.filter { _exportProgressMap.value[it.filename]?.savedUri == null }
            StorageFilter.ON_PHONE -> list.filter { _exportProgressMap.value[it.filename]?.savedUri != null }
        }

        // Step 3: Folder Filter
        if (_folderFilter.value != "ALL") {
            list = list.filter { it.folder.equals(_folderFilter.value, ignoreCase = true) }
        }

        // Step 4: Search Query Filter (Filename or Folder)
        val query = _searchQuery.value.trim().lowercase()
        if (query.isNotEmpty()) {
            list = list.filter {
                it.filename.lowercase().contains(query) || it.folder.lowercase().contains(query)
            }
        }

        // Step 5: Sorting
        list = when (_sortMode.value) {
            SortMode.DATE_DESC -> list.sortedWith(compareByDescending<CameraFile> { it.dateTime.ifEmpty { it.filename } }.thenByDescending { it.filename })
            SortMode.DATE_ASC -> list.sortedWith(compareBy<CameraFile> { it.dateTime.ifEmpty { it.filename } }.thenBy { it.filename })
            SortMode.NAME_ASC -> list.sortedBy { it.filename.lowercase() }
            SortMode.NAME_DESC -> list.sortedByDescending { it.filename.lowercase() }
            SortMode.FOLDER_ASC -> list.sortedWith(compareBy<CameraFile> { it.folder.lowercase() }.thenBy { it.filename.lowercase() })
        }

        _filteredFiles.value = list
        updateStatusSummary()
    }

    private fun updateStatusSummary() {
        val rawCount = _rawFiles.value.size
        val filteredCount = _filteredFiles.value.size
        val isFiltered = _selectedCategory.value != FileFilterCategory.ALL ||
                _storageFilter.value != StorageFilter.ALL ||
                _folderFilter.value != "ALL" ||
                _searchQuery.value.isNotBlank()

        _statusMessage.value = if (isFiltered) {
            "$rawCount kayıttan $filteredCount tanesi gösteriliyor"
        } else {
            "$rawCount medya kaydı tarandı."
        }
    }

    private fun updateExportProgress(filename: String, progress: FileExportProgress) {
        val current = _exportProgressMap.value.toMutableMap()
        current[filename] = progress
        _exportProgressMap.value = current
    }
}
