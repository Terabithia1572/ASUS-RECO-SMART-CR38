# ASUS RECO Smart / CR38 Android Client

> **Türkçe:** Bu proje ASUS RECO Smart / CR38 kamera için geliştirilen bağımsız ve resmi olmayan bir Android istemcisi ve protokol araştırma projesidir.  
> **English:** This project is an independent, unofficial Android client and protocol research application for the ASUS RECO Smart / CR38 camera.

---

# 🇹🇷 Türkçe

## 📱 Proje Hakkında

**ASUS RECO Smart / CR38 Android İstemcisi**, ASUS RECO Smart (CR38 model / SanJet DR38AS) araç ve aksiyon kamerasının Wi-Fi erişim noktası (Access Point) ağı üzerinden uzaktan kontrol edilmesini, canlı yayın izlenmesini, araç modu otomasyonunu, dosya yönetimini ve TCP komut protokolü hata ayıklamasını sağlayan bağımsız bir Android uygulamasıdır.

Orijinal mobil uygulamanın eski Android sürümlerine bağımlılığını ortadan kaldırmak ve kameranın donanım protokolünü belgelemek amacıyla modern Android mimarisi (**Kotlin**, **Jetpack Compose**, **MVVM**, **Coroutines**, **Media3/ExoPlayer**) kullanılarak sıfırdan geliştirilmiştir.

---

## ✨ Özellikler (Field Test RC7.2)

- **Fiziksel Kayıt Durumunun Sekme Geçişlerinden Ayrıştırılması**:
  - `LivePreviewScreen` sekmesinden çıkıldığında (`onDispose`) kamera tarafına `STOP_VF` (msg_id 260) veya `RESET_TO_VF` (msg_id 259) komutları gönderilmez. Sadece yerel Android `ExoPlayer` belleği serbest bırakılır.
  - Sekme gezinimleri (`Live` -> `Records`, `Settings`, `Connection`, `Protocol`, `About`) fiziksel kameranın video kaydını sonlandırmaz, kayıt kesintisiz devam eder.
  - Canlı Önizleme sekmesine yeniden dönüldüğünde kameranın aktif kayıt durumu korunur (`isRecording == true`), tahrip edici `RESET_TO_VF` veya `STOP_VF` sıfırlamaları atlanarak RTSP akışına (`rtsp://192.168.42.1/live`) doğrudan bağlanılır.
- **Komut Kaynağı (Origin) İzleme**:
  - Tüm protokol komutlarına gönderim kaynağı eklendi (örn: `[CMD][origin=USER_RECORD_BUTTON] RECORD_START msg_id=513`, `[CMD][origin=LIVE_SCREEN_ENTER] RESET_TO_VF`, `[LOCAL][origin=LIVE_SCREEN_EXIT] RTSP renderer released`).
- **Güvenli Kayıt Sonlandırma ve Anında Dosya Keşfi (RC7.1 Baselines)**:
  - Sadece kullanıcı kırmızı Kaydı Durdur butonuna bastığında `RECORD_STOP` komutu gönderilir.
  - Komut onayından sonra dosya sistemi stabilizasyon beklemesi (~800 ms) yapılır ve yeni oluşturulan MP4 dosyası otomatik keşfedilir (`[REC] new media discovered: <filename>`).
- **Kamera Kayıtlarında Yerel Arama & Sıralama**:
  - Dosya ve klasör isimlerine göre (örn: `FILE4089`, `EMRG`, `113MEDIA`, `116MEDIA`) harf büyüklüğüne duyarsız (case-insensitive) anlık arama ve esnek sıralama (Tarih Yeniden Eskiye/Eskiden Yeniye, İsim A-Z/Z-A, Klasör A-Z).
- **Gelişmiş Filtreleme & Dinamik Klasör Süzgeçleri**:
  - Yatay kaydırılabilir responsive çip düzeni ile **Kategori**, **Lokasyon** (Kamerada, Telefonda) ve **Klasör** süzgeçleri.
- **Esnek ve Responsive Arayüz Düzenlemeleri**:
  - Dar ekranlı telefonlarda eylem butonları (`Kaydet`, `Aç`, `Paylaş`) ve durum rozetleri (`Kamerada`, `Telefonda`) tek satırda hizalanır.

---

## 📸 Ekran Görüntüleri

<p align="center">
  <img src="docs/screenshots/connection.jpg" width="240" alt="Bağlantı Ekranı" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/live-view.jpg" width="240" alt="Canlı Görüntü" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/storage.jpg" width="240" alt="Kamera Kayıtları" />
</p>
<p align="center">
  <b>Bağlantı & Araç Göstergesi</b> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
  <b>RTSP Canlı Görüntü & Çekim</b> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
  <b>SD Kart Dosya Yöneticisi</b>
</p>

---

## 🏗️ Mimari

Uygulama, **Clean Architecture** ve **MVVM (Model-View-ViewModel)** prensiplerine uygun olarak tasarlanmıştır:

```text
               Android UI (Jetpack Compose)
                            │
                       ViewModel
                            │
                    CameraRepository
                   /                \
  MockCameraRepository            DefaultCameraRepository
  (Çevrimdışı Simülasyon)                 │
                                  ├── CameraNetworkManager (Wi-Fi Soket Kilidi)
                                  ├── TcpSocketClient (Port 7878 Soket Motoru)
                                  ├── SessionManager (Token Yönetimi)
                                  └── Protocol Parsers (JSON Framer / Serializer)
                                          │
                                          ▼
                                SanJet DR38AS / CR38
```

---

## 🌐 Kamera Haberleşme Parametreleri

| Parametre | Standart Değer | Açıklama |
| :--- | :--- | :--- |
| **Kamera IP Adresi** | `192.168.42.1` | CR38 Wi-Fi Ağındaki Varsayılan İletişim IP'si |
| **Komut Portu (TCP)** | `7878` | JSON Komut ve Yanıt Protokol Soketi |
| **Veri Portu (TCP)** | `8787` | Dosya Aktarım ve Binary Veri Kanalı |
| **RTSP Canlı Yayın** | `rtsp://192.168.42.1/live` | H.264 Canlı Video Akış Adresi |
| **DCIM Kök Dizini** | `/tmp/fuse_d/DCIM/` | SD Kart Ortam Dosyaları Ana Dizini |

---

## 🚧 Donanım Doğrulama Durumu (Field Test RC7.2)

| Katman | Durum |
| :--- | :--- |
| **Simülasyon / Mock Modu** | ✅ `VERIFIED` (11/11 Self-Test Passed) |
| **Birim Test Paketi** | ✅ `VERIFIED` (72/72 Unit Tests Passed) |
| **Android Kod Tabanı & Derleme** | ✅ `VERIFIED` (Clean Build RC7.2) |
| **Fiziksel CR38 Donanım Doğrulaması** | ✅ `FIELD TEST RC7.2 VERIFIED` (Fiziksel Donanım Kayıt Ayrıştırması Doğrulandı) |

---

## 🛠️ Kullanılan Teknolojiler

- **Dil**: Kotlin 1.9+
- **Kullanıcı Arayüzü**: Jetpack Compose (Material Design 3)
- **Asenkron Motor**: Kotlin Coroutines & Flow
- **Medya / Video**: AndroidX Media3 / ExoPlayer (RTSP Support)
- **Ağ / Soket**: Java/Android TCP Sockets, `ConnectivityManager` Network Binding
- **Derleme Sistemi**: Gradle 8.14 (Kotlin DSL)

---

## 🚀 Geliştirme ve Derleme

```cmd
:: Birincil birim testlerini çalıştırma
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat test

:: Debug APK paketini oluşturma
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat clean assembleDebug
```

Derlenen APK dosyası `app/build/outputs/apk/debug/app-debug.apk` konumunda oluşturulur.

---

## ⚖️ Yasal Uyarı

Bu proje bağımsız bir araştırma ve geliştirme çalışmasıdır.  
**ASUS ve RECO Smart markaları ASUSTeK Computer Inc. tescilli markalarıdır. Bu uygulama bağımsız açık kaynaklı topluluk kontrol yazılımıdır.**

---

# 🇬🇧 English

# ASUS RECO Smart / CR38 Android Client

## 📱 About the Project

**ASUS RECO Smart / CR38 Android Client** is an independent, unofficial Android application developed for remote control, RTSP live view streaming, vehicle automation, camera storage management, and low-level TCP protocol diagnostics of the **ASUS RECO Smart (CR38 / SanJet DR38AS)** Action & Dash Camera via its local Wi-Fi Access Point network.

Built from scratch using modern Android architecture (**Kotlin**, **Jetpack Compose**, **MVVM**, **Coroutines**, and **Media3/ExoPlayer**).

---

## ✨ Features (Field Test RC7.2)

- **Recording Lifecycle Decoupling**:
  - Leaving `LivePreviewScreen` (`onDispose`) releases only local Android `ExoPlayer` memory without sending camera-side `STOP_VF` (msg_id 260) or `RESET_TO_VF` (msg_id 259) commands.
  - Tab navigation (`Live` -> `Records`, `Settings`, `Connection`, `Protocol`, `About`) preserves physical camera MP4 recording uninterrupted.
  - Re-entering Live Preview while recording (`isRecording == true`) bypasses destructive `RESET_TO_VF` pipeline resets, attaching directly to `rtsp://192.168.42.1/live`.
- **Command Origin Tracking**:
  - All protocol log lines contain explicit origins (e.g., `[CMD][origin=USER_RECORD_BUTTON] RECORD_START msg_id=513`, `[CMD][origin=LIVE_SCREEN_ENTER] RESET_TO_VF`, `[LOCAL][origin=LIVE_SCREEN_EXIT] RTSP renderer released`).
- **Safe Recording Completion & Instant File Discovery**:
  - Only explicit user Stop button action sends `RECORD_STOP`, followed by filesystem stabilization wait (~800 ms) and automatic new file discovery (`[REC] new media discovered: <filename>`).
- **Camera Records Local Search & Sorting**:
  - Case-insensitive local search and flexible sorting (Date, Name, Folder).

---

## 🚀 Building & Setup

```bash
# Run unit tests (Windows)
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat test

# Assemble debug APK
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat clean assembleDebug
```

---

## ⚖️ Legal Disclaimer

This project is an independent research implementation.  
**ASUS and RECO Smart are registered trademarks of ASUSTeK Computer Inc. This application is an independent community project.**
