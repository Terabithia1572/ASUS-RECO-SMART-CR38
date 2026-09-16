package com.asus.recosmart.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asus.recosmart.RecoSmartApp
import com.asus.recosmart.domain.model.CameraSetting
import com.asus.recosmart.domain.model.CameraSettingOption
import com.asus.recosmart.domain.model.CameraSettingSpec
import com.asus.recosmart.domain.model.SessionState
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingCategory(val title: String) {
    VIDEO("Video Ayarları"),
    PHOTO("Fotoğraf Ayarları"),
    SECURITY("Güvenlik ve Sürüş"),
    IMAGE("Görüntü ve Tarih Damgası"),
    SYSTEM("Sistem ve Bakım")
}

data class CategorizedSettings(
    val category: SettingCategory,
    val specs: List<CameraSettingSpec>
)

class SettingsViewModel(
    private val repository: CameraRepository = RecoSmartApp.instance.cameraRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = repository.sessionState
    val isMockMode: StateFlow<Boolean> = repository.isMockMode

    private val _settingSpecs = MutableStateFlow<List<CameraSettingSpec>>(emptyList())
    val settingSpecs: StateFlow<List<CameraSettingSpec>> = _settingSpecs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSettingMutationInProgress = MutableStateFlow(false)
    val isSettingMutationInProgress: StateFlow<Boolean> = _isSettingMutationInProgress.asStateFlow()

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        if (_isSettingMutationInProgress.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = "Kamera ayarları alınıyor..."
            val res = repository.fetchAllSettings()
            if (res.isSuccess) {
                val fetchedSettings = res.getOrDefault(emptyList()).associateBy { it.key }
                _settingSpecs.value = buildSettingSpecs(fetchedSettings)
                _statusText.value = "Ayarlar başarıyla yüklendi"
            } else {
                _settingSpecs.value = buildSettingSpecs(emptyMap())
                _statusText.value = "Kamera ayarları alınamadı"
            }
            _isLoading.value = false
        }
    }

    fun syncCameraClock() {
        if (_isSettingMutationInProgress.value) return
        viewModelScope.launch {
            _isSettingMutationInProgress.value = true
            _statusText.value = "Kamera saati telefonla eşitleniyor..."
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val formattedTime = sdf.format(Date())
            repository.logRtsp("[CLOCK SYNC] Synchronizing camera clock to local phone time: '$formattedTime'")

            val res = repository.updateSetting("camera_clock", formattedTime)
            if (res.isSuccess && res.getOrNull()?.isSuccess == true) {
                _statusText.value = "Kamera saati başarıyla eşitlendi: $formattedTime"
            } else {
                val resFallback = repository.updateSetting("time", formattedTime)
                if (resFallback.isSuccess && resFallback.getOrNull()?.isSuccess == true) {
                    _statusText.value = "Kamera saati başarıyla eşitlendi: $formattedTime"
                } else {
                    _statusText.value = "Kamera saati eşitleme komutu gönderildi: $formattedTime"
                }
            }
            _isSettingMutationInProgress.value = false
        }
    }

    fun updateSetting(key: String, wireValue: String) {
        if (_isSettingMutationInProgress.value) {
            repository.logRtsp("[SETTINGS REJECTED] Mutation already in progress. Request key='$key' ignored.")
            return
        }

        viewModelScope.launch {
            _isSettingMutationInProgress.value = true
            val spec = _settingSpecs.value.find { it.key == key }
            val optionName = spec?.options?.find { it.wireValue == wireValue }?.displayName ?: wireValue

            if (key == "video_resolution") {
                executeRecordingAwareResolutionChange(key, wireValue, optionName, spec)
            } else {
                executeStandardSettingUpdate(key, wireValue, optionName, spec)
            }

            _isSettingMutationInProgress.value = false
        }
    }

    private suspend fun executeRecordingAwareResolutionChange(
        key: String,
        wireValue: String,
        optionName: String,
        spec: CameraSettingSpec?
    ) {
        val wasRecordingBefore = repository.cameraStatus.value.isRecording
        repository.logRtsp("[RESOLUTION] ==================================================")
        repository.logRtsp("[RESOLUTION] Target resolution: '$optionName' ($wireValue)")
        repository.logRtsp("[RESOLUTION] Recording state before change: ${if (wasRecordingBefore) "RECORDING" else "NOT_RECORDING"}")

        if (wasRecordingBefore) {
            _statusText.value = "Video çözünürlüğü değiştirmek için kayıt geçici olarak durduruluyor..."
            repository.logRtsp("[RESOLUTION] Step 1/6: Sending RECORD_STOP...")
            val stopStartTime = System.currentTimeMillis()
            val stopRes = repository.stopRecording()
            val stopElapsed = System.currentTimeMillis() - stopStartTime
            repository.logRtsp("[RESOLUTION] RECORD_STOP acknowledged after ${stopElapsed}ms (success=${stopRes.isSuccess})")

            repository.logRtsp("[RESOLUTION] Step 2/6: Waiting for encoder stabilization (600ms)...")
            delay(600)
        }

        _statusText.value = "Video çözünürlüğü uygulanıyor..."
        repository.logRtsp("[RESOLUTION] Step 3/6: Sending SET_SETTING video_resolution='$wireValue'...")
        val setStartTime = System.currentTimeMillis()
        val setRes = repository.updateSetting(key, wireValue)
        val setElapsed = System.currentTimeMillis() - setStartTime
        val setAckSuccess = setRes.isSuccess && setRes.getOrNull()?.isSuccess == true
        repository.logRtsp("[RESOLUTION] SET_SETTING acknowledged after ${setElapsed}ms (success=$setAckSuccess)")

        _statusText.value = "Donanım ayarı işliyor, bekleniyor..."
        repository.logRtsp("[RESOLUTION] Step 4/6: Waiting for hardware encoder re-initialization (1200ms)...")
        delay(1200)

        _statusText.value = "Video çözünürlüğü doğrulanıyor..."
        repository.logRtsp("[RESOLUTION] Step 5/6: Verifying setting via GET_ALL_CURRENT_SETTINGS...")
        var verifyRes = repository.fetchAllSettings()
        var recoveredSession = false

        if (verifyRes.isFailure && !isMockMode.value) {
            repository.logRtsp("[RESOLUTION NOTICE] Read-back socket failed. Attempting 1-shot session recovery...")
            _statusText.value = "Ayar uygulanırken kamera bağlantısı yenileniyor..."
            val recRes = repository.recoverSession()
            if (recRes.isSuccess) {
                recoveredSession = true
                repository.logRtsp("[RESOLUTION RECOVERY] Session recovered cleanly. Retrying read-back...")
                delay(500)
                verifyRes = repository.fetchAllSettings()
            }
        }

        var confirmedVal: String? = null
        if (verifyRes.isSuccess) {
            val settingsMap = verifyRes.getOrDefault(emptyList()).associateBy { it.key }
            confirmedVal = settingsMap[key]?.value
            _settingSpecs.value = buildSettingSpecs(settingsMap)
        }
        val isVerified = confirmedVal == wireValue

        repository.logRtsp("[RESOLUTION] Restoring viewfinder stream state...")
        repository.resetToVf()

        if (wasRecordingBefore && repository.sessionState.value is SessionState.Connected) {
            _statusText.value = "Kayıt yeniden başlatılıyor..."
            repository.logRtsp("[RESOLUTION] Step 6/6: Restarting previous recording state (RECORD_START)...")
            val recStartTime = System.currentTimeMillis()
            val startRes = repository.startRecording()
            val recElapsed = System.currentTimeMillis() - recStartTime
            repository.logRtsp("[RESOLUTION] RECORD_START acknowledged after ${recElapsed}ms (success=${startRes.isSuccess})")
        }

        when {
            isVerified -> {
                _statusText.value = "Video çözünürlüğü doğrulandı: $optionName"
                repository.logRtsp("[RESOLUTION] COMPLETE: Video resolution updated and verified as $optionName ($wireValue)")
            }
            confirmedVal != null && confirmedVal != wireValue -> {
                _statusText.value = "Video çözünürlüğü değiştirilemedi: Kamera eski değeri korudu ($confirmedVal)"
                repository.logRtsp("[RESOLUTION] REJECTED: Camera retained '$confirmedVal' instead of '$wireValue'")
            }
            setAckSuccess -> {
                _statusText.value = if (recoveredSession) "Ayar kameraya gönderildi, oturum yenilendi (Doğrulama okunamadı)" else "Ayar kameraya gönderildi ancak doğrulama okunamadı."
                repository.logRtsp("[RESOLUTION INDETERMINATE] SET_SETTING ACK succeeded but read-back remained unverified.")
            }
            else -> {
                val err = setRes.exceptionOrNull()?.localizedMessage ?: "Komut reddedildi"
                _statusText.value = "Video çözünürlüğü değiştirilemedi: $err"
                repository.logRtsp("[RESOLUTION FAILED] $err")
            }
        }
        repository.logRtsp("[RESOLUTION] ==================================================")
    }

    private suspend fun executeStandardSettingUpdate(
        key: String,
        wireValue: String,
        optionName: String,
        spec: CameraSettingSpec?
    ) {
        val label = spec?.label ?: key
        _statusText.value = "$label -> $optionName güncelleniyor..."
        repository.logRtsp("[SETTINGS WRITE] Key='$key', Param='$wireValue' requested...")

        val setRes = repository.updateSetting(key, wireValue)
        val setAckSuccess = setRes.isSuccess && setRes.getOrNull()?.isSuccess == true

        delay(800)

        var verifyRes = repository.fetchAllSettings()
        var recoveredSession = false

        if (verifyRes.isFailure && !isMockMode.value) {
            repository.logRtsp("[SETTINGS WRITE NOTICE] Read-back socket failed. Attempting 1-shot session recovery...")
            _statusText.value = "Ayar uygulanırken kamera bağlantısı yenileniyor..."
            val recRes = repository.recoverSession()
            if (recRes.isSuccess) {
                recoveredSession = true
                delay(500)
                verifyRes = repository.fetchAllSettings()
            }
        }

        if (verifyRes.isSuccess) {
            val settingsMap = verifyRes.getOrDefault(emptyList()).associateBy { it.key }
            val confirmedVal = settingsMap[key]?.value
            if (confirmedVal == wireValue) {
                _settingSpecs.value = buildSettingSpecs(settingsMap)
                _statusText.value = "$label -> $optionName olarak güncellendi ve doğrulandı."
                repository.logRtsp("[SETTINGS WRITE CONFIRMED] Key='$key' confirmed on camera as '$wireValue'")
            } else if (confirmedVal != null) {
                _statusText.value = "$label güncellenemedi (Kamera eski değeri korudu: $confirmedVal)"
                repository.logRtsp("[SETTINGS WRITE REJECTED] Camera retained '$confirmedVal' instead of '$wireValue'")
            } else {
                _statusText.value = "$label güncellendi."
            }
        } else if (setAckSuccess) {
            _statusText.value = if (recoveredSession) "Kamera ayarı aldı, oturum yenilendi." else "Kamera ayarı aldı ancak doğrulama okunamadı."
            repository.logRtsp("[SETTINGS WRITE INDETERMINATE] ACK succeeded, read-back unavailable.")
        } else {
            val err = setRes.exceptionOrNull()?.localizedMessage ?: "Komut reddedildi"
            _statusText.value = "$label güncellenemedi: $err"
            repository.logRtsp("[SETTINGS WRITE FAIL] Key='$key' failed: $err")
        }
    }

    fun formatSdCard() {
        if (_isSettingMutationInProgress.value) return
        viewModelScope.launch {
            _statusText.value = "SD Kart biçimlendiriliyor..."
            val res = repository.formatSdCard()
            if (res.isSuccess) {
                _statusText.value = "SD Kart başarıyla biçimlendirildi!"
            } else {
                _statusText.value = "SD Kart biçimlendirme başarısız: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun factoryReset() {
        if (_isSettingMutationInProgress.value) return
        viewModelScope.launch {
            _statusText.value = "Fabrika ayarlarına dönülüyor..."
            val res = repository.factoryReset()
            if (res.isSuccess) {
                _statusText.value = "Kamera fabrika ayarlarına döndürüldü!"
                loadSettings()
            } else {
                _statusText.value = "Fabrika ayarlarına dönme başarısız"
            }
        }
    }

    fun resetToVf() {
        if (_isSettingMutationInProgress.value) return
        viewModelScope.launch {
            _statusText.value = "Kamera canlı görüntü moduna sıfırlanıyor..."
            val res = repository.resetToVf()
            _statusText.value = if (res.isSuccess) "Kamera canlı görüntü moduna alındı" else "Canlı görüntü moduna alma başarısız"
        }
    }

    private fun buildSettingSpecs(fetched: Map<String, CameraSetting>): List<CameraSettingSpec> {
        val defaultVideoRes = fetched["video_resolution"]?.value ?: "1920x1080 30P 16:9"
        val defaultPhotoSize = fetched["photo_size"]?.value ?: "16M (4608x3456 4:3)"
        val defaultRecMode = fetched["rec mode"]?.value ?: "continuous"
        val defaultLoopRec = fetched["loop record"]?.value ?: "LOOP_REC_3_MIN"
        val defaultGSensor = fetched["gs sense"]?.value ?: "medium"
        val defaultMotionDet = fetched["motion_det"]?.value ?: "off"
        val defaultLdws = fetched["ldws"]?.value ?: "off"
        val defaultFcws = fetched["fcws"]?.value ?: "off"
        val defaultPhotoBurst = fetched["photo burst"]?.value ?: "5p1s"
        val defaultTimeLapse = fetched["photo time lapse"]?.value ?: "1ps"
        val defaultEV = fetched["ev value"]?.value ?: "0.0ev"
        val defaultDateStamp = fetched["date_stamp"]?.value ?: "ON"
        val defaultRotate = fetched["image rotate"]?.value ?: "NORMAL"
        val defaultStartupRec = fetched["startup_record"]?.value ?: "ON"
        val defaultSysMode = fetched["sys mode"]?.value ?: "CAR_MODE"
        val defaultLang = fetched["language"]?.value ?: "ENG"
        val defaultMic = fetched["microphone"]?.value ?: "on"
        val defaultAutoPowerOff = fetched["auto_power_off"]?.value ?: "5MIN"
        val defaultSsid = fetched["wifi_ssid"]?.value ?: "CR38_ASUS_9F82"

        return listOf(
            // Video
            CameraSettingSpec(
                key = "video_resolution",
                label = "Video Çözünürlüğü",
                description = "Kayıt formatı ve kare hızı (FPS)",
                options = listOf(
                    CameraSettingOption("1920×1080 • 30 FPS", "1920x1080 30P 16:9"),
                    CameraSettingOption("1280×720 • 60 FPS", "1280x720 60P 16:9"),
                    CameraSettingOption("1280×720 • 30 FPS", "1280x720 30P 16:9"),
                    CameraSettingOption("1920×1080 HDR (Doğrulanmadı / CR38 Desteklemiyor)", "HDR 1920x1080 30P 16:9"),
                    CameraSettingOption("2304×1296 • 30 FPS", "2304x1296 30P 16:9"),
                    CameraSettingOption("2560×1080 • 30 FPS", "2560x1080 30P 21:9")
                ),
                currentWireValue = defaultVideoRes
            ),
            CameraSettingSpec(
                key = "loop record",
                label = "Döngüsel Kayıt Süresi",
                description = "Acil durum çarpışma döngü dosya süresi",
                options = listOf(
                    CameraSettingOption("1 Dakika", "LOOP_REC_1_MIN"),
                    CameraSettingOption("3 Dakika", "LOOP_REC_3_MIN"),
                    CameraSettingOption("5 Dakika", "LOOP_REC_5_MIN")
                ),
                currentWireValue = defaultLoopRec
            ),
            CameraSettingSpec(
                key = "microphone",
                label = "Mikrofon Ses Kaydı",
                description = "Araç içi ses kaydı",
                options = listOf(
                    CameraSettingOption("Açık", "on"),
                    CameraSettingOption("Kapalı", "off")
                ),
                currentWireValue = defaultMic
            ),
            CameraSettingSpec(
                key = "ev value",
                label = "Pozlama Değeri (EV)",
                description = "Sensör pozlama telafisi",
                options = listOf(
                    CameraSettingOption("-2.0 EV", "-2.0ev"),
                    CameraSettingOption("-1.0 EV", "-1.0ev"),
                    CameraSettingOption("0.0 EV (Standart)", "0.0ev"),
                    CameraSettingOption("+1.0 EV", "1.0ev"),
                    CameraSettingOption("+2.0 EV", "2.0ev")
                ),
                currentWireValue = defaultEV
            ),

            // Fotoğraf
            CameraSettingSpec(
                key = "photo_size",
                label = "Fotoğraf Çözünürlüğü",
                description = "Fotoğraf boyutları (Kamera wire ayarı)",
                options = listOf(
                    CameraSettingOption("16 MP • 4608×3456 (4:3)", "16M (4608x3456 4:3)"),
                    CameraSettingOption("8 MP • 3264×2448 (4:3)", "8M (3264x2448 4:3)"),
                    CameraSettingOption("3.8 MP • 2304×1536 (3:2)", "3.8M (2304x1536 3:2)"),
                    CameraSettingOption("3 MP • 2048×1536 (4:3)", "3M (2048x1536 4:3)"),
                    CameraSettingOption("2.8 MP • 2304×1296 (16:9)", "2.8M (2304x1296 16:9)"),
                    CameraSettingOption("0.3 MP • 640×480 (4:3)", "0.3M (640x480 4:3)")
                ),
                currentWireValue = defaultPhotoSize
            ),
            CameraSettingSpec(
                key = "photo burst",
                label = "Seri Çekim Modu",
                description = "Saniye başına seri fotoğraf sayısı",
                options = listOf(
                    CameraSettingOption("5 fotoğraf / 1 sn", "5p1s"),
                    CameraSettingOption("10 fotoğraf / 1 sn", "10p1s"),
                    CameraSettingOption("30 fotoğraf / 1 sn", "30p1s")
                ),
                currentWireValue = defaultPhotoBurst
            ),
            CameraSettingSpec(
                key = "photo time lapse",
                label = "Zaman Atlamalı Çekim",
                description = "Time-lapse fotoğraf çekim aralığı",
                options = listOf(
                    CameraSettingOption("Her 1 saniyede", "1ps"),
                    CameraSettingOption("Her 5 saniyede", "1p5s"),
                    CameraSettingOption("Her 30 saniyede", "1p30s"),
                    CameraSettingOption("Her 1 dakikada", "1pm")
                ),
                currentWireValue = defaultTimeLapse
            ),

            // Güvenlik
            CameraSettingSpec(
                key = "gs sense",
                label = "G-Sensör Hassasiyeti",
                description = "Darbe ve çarpışma algılama tetikleyicisi",
                options = listOf(
                    CameraSettingOption("Kapalı", "off"),
                    CameraSettingOption("Düşük Hassasiyet", "low"),
                    CameraSettingOption("Orta Hassasiyet", "medium"),
                    CameraSettingOption("Yüksek Hassasiyet", "high")
                ),
                currentWireValue = defaultGSensor
            ),
            CameraSettingSpec(
                key = "motion_det",
                label = "Hareket Algılama (Deneysel)",
                description = "Park modunda hareket algılama",
                options = listOf(
                    CameraSettingOption("Kapalı", "off"),
                    CameraSettingOption("Açık", "on")
                ),
                currentWireValue = defaultMotionDet
            ),
            CameraSettingSpec(
                key = "ldws",
                label = "LDWS Şerit Takip (Deneysel)",
                description = "Şerit terk uyarı sistemi",
                options = listOf(
                    CameraSettingOption("Kapalı", "off"),
                    CameraSettingOption("Açık", "on")
                ),
                currentWireValue = defaultLdws
            ),
            CameraSettingSpec(
                key = "fcws",
                label = "FCWS Çarpışma Uyarısı (Deneysel)",
                description = "Ön çarpışma uyarı sistemi",
                options = listOf(
                    CameraSettingOption("Kapalı", "off"),
                    CameraSettingOption("Açık", "on")
                ),
                currentWireValue = defaultFcws
            ),

            // Görüntü
            CameraSettingSpec(
                key = "date_stamp",
                label = "Tarih / Saat Damgası",
                description = "Videolar üzerine tarih ve saat yazdırma",
                options = listOf(
                    CameraSettingOption("Açık", "ON"),
                    CameraSettingOption("Kapalı", "OFF")
                ),
                currentWireValue = defaultDateStamp
            ),
            CameraSettingSpec(
                key = "image rotate",
                label = "Görüntü Yönü",
                description = "Kamera montaj yönü döndürme",
                options = listOf(
                    CameraSettingOption("Otomatik Algılama", "AUTO_DETECT"),
                    CameraSettingOption("Normal", "NORMAL"),
                    CameraSettingOption("Ters (Döndürülmüş)", "REVERSE")
                ),
                currentWireValue = defaultRotate
            ),

            // Sistem
            CameraSettingSpec(
                key = "startup_record",
                label = "Başlangıçta Otomatik Kayıt",
                description = "Kamera 12V güç aldığında otomatik kayda başla",
                options = listOf(
                    CameraSettingOption("Açık", "ON"),
                    CameraSettingOption("Kapalı", "OFF")
                ),
                currentWireValue = defaultStartupRec
            ),
            CameraSettingSpec(
                key = "sys mode",
                label = "Sistem Modu",
                description = "Araç kamerası / taşınabilir mod profili",
                options = listOf(
                    CameraSettingOption("Araç Modu", "CAR_MODE"),
                    CameraSettingOption("Taşınabilir Mod", "WEARABLE_MODE")
                ),
                currentWireValue = defaultSysMode
            ),
            CameraSettingSpec(
                key = "language",
                label = "Kamera Dili",
                description = "Kamera sistem dili",
                options = listOf(
                    CameraSettingOption("İngilizce", "ENG"),
                    CameraSettingOption("Geleneksel Çince (CHT)", "CHT"),
                    CameraSettingOption("Basitleştirilmiş Çince (CHS)", "CHS")
                ),
                currentWireValue = defaultLang
            ),
            CameraSettingSpec(
                key = "auto_power_off",
                label = "Otomatik Kapanma",
                description = "Salt okunur sistem koruma ayarı",
                options = emptyList(),
                isEditable = false,
                currentWireValue = defaultAutoPowerOff
            ),
            CameraSettingSpec(
                key = "wifi_ssid",
                label = "Wi-Fi Ağ Adı",
                description = "Salt okunur sistem koruma ayarı",
                options = emptyList(),
                isEditable = false,
                currentWireValue = defaultSsid
            )
        )
    }
}
