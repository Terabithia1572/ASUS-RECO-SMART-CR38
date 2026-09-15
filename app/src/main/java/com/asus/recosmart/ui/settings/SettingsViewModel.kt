package com.asus.recosmart.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asus.recosmart.RecoSmartApp
import com.asus.recosmart.domain.model.CameraSetting
import com.asus.recosmart.domain.model.CameraSettingOption
import com.asus.recosmart.domain.model.CameraSettingSpec
import com.asus.recosmart.domain.model.SessionState
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: CameraRepository = RecoSmartApp.instance.cameraRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = repository.sessionState
    val isMockMode: StateFlow<Boolean> = repository.isMockMode

    private val _settingSpecs = MutableStateFlow<List<CameraSettingSpec>>(emptyList())
    val settingSpecs: StateFlow<List<CameraSettingSpec>> = _settingSpecs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
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

    fun updateSetting(key: String, wireValue: String) {
        viewModelScope.launch {
            val spec = _settingSpecs.value.find { it.key == key }
            val optionName = spec?.options?.find { it.wireValue == wireValue }?.displayName ?: wireValue
            _statusText.value = "${spec?.label ?: key} -> $optionName güncelleniyor..."

            val res = repository.updateSetting(key, wireValue)
            if (res.isSuccess) {
                _statusText.value = "${spec?.label ?: key} -> $optionName olarak güncellendi"
                loadSettings()
            } else {
                _statusText.value = "${spec?.label ?: key} güncellenemedi"
            }
        }
    }

    fun formatSdCard() {
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
        val defaultPhotoBurst = fetched["photo burst"]?.value ?: "5p1s"
        val defaultTimeLapse = fetched["photo time lapse"]?.value ?: "1ps"
        val defaultEV = fetched["ev value"]?.value ?: "0.0ev"
        val defaultRotate = fetched["image rotate"]?.value ?: "NORMAL"
        val defaultSysMode = fetched["sys mode"]?.value ?: "CAR_MODE"
        val defaultLang = fetched["language"]?.value ?: "ENG"
        val defaultMic = fetched["microphone"]?.value ?: "on"
        val defaultAutoPowerOff = fetched["auto_power_off"]?.value ?: "5MIN"
        val defaultSsid = fetched["wifi_ssid"]?.value ?: "CR38_ASUS_9F82"

        return listOf(
            CameraSettingSpec(
                key = "video_resolution",
                label = "Video Çözünürlüğü",
                description = "Kayıt formatı ve kare hızı (FPS)",
                options = listOf(
                    CameraSettingOption("1920×1080 • 30 FPS", "1920x1080 30P 16:9"),
                    CameraSettingOption("1280×720 • 60 FPS", "1280x720 60P 16:9"),
                    CameraSettingOption("1280×720 • 30 FPS", "1280x720 30P 16:9"),
                    CameraSettingOption("1920×1080 HDR • 30 FPS", "HDR 1920x1080 30P 16:9"),
                    CameraSettingOption("2304×1296 • 30 FPS", "2304x1296 30P 16:9"),
                    CameraSettingOption("2560×1080 • 30 FPS", "2560x1080 30P 21:9"),
                    // DECOMPILED SOURCE CONFLICT:
                    // DeviceCommand.java transmits "432x240 240P 16:9" while another parser references "432x240 120P 16:9".
                    // Preserving wire value "432x240 240P 16:9".
                    // UNVERIFIED — REQUIRES PHYSICAL CR38 TEST
                    CameraSettingOption("432×240 Yüksek Hız (DOĞRULANMADI)", "432x240 240P 16:9")
                ),
                currentWireValue = defaultVideoRes
            ),
            CameraSettingSpec(
                key = "photo_size",
                label = "Fotoğraf Çözünürlüğü",
                description = "Fotoğraf boyutları ve en-boy oranı",
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
                key = "rec mode",
                label = "Kayıt Modu",
                description = "Video kayıt süre profili",
                options = listOf(
                    CameraSettingOption("Sürekli", "continuous"),
                    CameraSettingOption("5 Dakika", "5min"),
                    CameraSettingOption("3 Dakika", "3min")
                ),
                currentWireValue = defaultRecMode
            ),
            CameraSettingSpec(
                key = "loop record",
                label = "Döngüsel Kayıt",
                description = "Acil durum çarpışma döngü dosya süresi",
                options = listOf(
                    CameraSettingOption("1 Dakika", "LOOP_REC_1_MIN"),
                    CameraSettingOption("3 Dakika", "LOOP_REC_3_MIN"),
                    CameraSettingOption("5 Dakika", "LOOP_REC_5_MIN")
                ),
                currentWireValue = defaultLoopRec
            ),
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
                key = "microphone",
                label = "Mikrofon",
                description = "Araç içi ses kaydı",
                options = listOf(
                    CameraSettingOption("Açık", "on"),
                    CameraSettingOption("Kapalı", "off")
                ),
                currentWireValue = defaultMic
            ),
            CameraSettingSpec(
                key = "auto_power_off",
                label = "Otomatik Kapanma",
                description = "Bu ayarın yazma komutu ASUS kaynak kodunda güvenli şekilde doğrulanamadığı için uygulamadan değiştirilemez.",
                options = emptyList(),
                isEditable = false,
                currentWireValue = defaultAutoPowerOff
            ),
            CameraSettingSpec(
                key = "wifi_ssid",
                label = "Wi-Fi Ağ Adı",
                description = "Bu ayarın yazma komutu ASUS kaynak kodunda güvenli şekilde doğrulanamadığı için uygulamadan değiştirilemez.",
                options = emptyList(),
                isEditable = false,
                currentWireValue = defaultSsid
            )
        )
    }
}
