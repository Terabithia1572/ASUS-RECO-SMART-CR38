# ASUS RECO Smart / CR38 Android Client

> **Türkçe:** Bu proje ASUS RECO Smart / CR38 kamera için geliştirilen bağımsız ve resmi olmayan bir Android istemcisi ve protokol araştırma projesidir.  
> **English:** This project is an independent, unofficial Android client and protocol research application for the ASUS RECO Smart / CR38 camera.

---

# 🇹🇷 Türkçe

## 📱 Proje Hakkında

**ASUS RECO Smart / CR38 Android İstemcisi**, ASUS RECO Smart (CR38 model / SanJet DR38AS) araç ve aksiyon kamerasının Wi-Fi erişim noktası (Access Point) ağı üzerinden uzaktan kontrol edilmesini, canlı yayın izlenmesini, araç modu otomasyonunu, dosya yönetimini ve TCP komut protokolü hata ayıklamasını sağlayan bağımsız bir Android uygulamasıdır.

Orijinal mobil uygulamanın eski Android sürümlerine bağımlılığını ortadan kaldırmak ve kameranın donanım protokolünü belgelemek amacıyla modern Android mimarisi (**Kotlin**, **Jetpack Compose**, **MVVM**, **Coroutines**, **Media3/ExoPlayer**) kullanılarak sıfırdan geliştirilmiştir.

---

## ✨ Özellikler (Field Test RC7.1)

- **Sekme Geziniminde Kayıt Durumunun Korunması**:
  - Kameranın kayıt durumu `CameraRepository` (`cameraStatus`) seviyesinde authoritative (otoritatif) cihaz/oturum durumu olarak yönetilir.
  - Sekmeler arası geçişlerde (`LivePreviewScreen` kapansa bile) `RECORD_STOP` veya `RECORD_START` komutları otomatik olarak tetiklenmez.
  - Canlı Önizlemeye dönüldüğünde kameranın aktif kayıt durumu hemen algılanır ve kırmızı "KAYIT" durumu gösterilir.
  - Canlı görüntü başlatma işlemi `idempotent` ve tekilleştirilmiş (serialized) hale getirilerek mükerrer `RESET_TO_VF` işlemleri önlenmiştir.
- **Güvenli Kayıt Sonlandırma ve Anında Dosya Keşfi**:
  - `RECORD_STOP` komutu gönderildikten sonra komut onayı (`acknowledgement`) beklenir ve kamera dosya sisteminin stabilize olması için kısa süreli (~500-1000 ms) dinamik bekleme sağlanır.
  - Kamera bağlantısı/oturumu koparılmadan DCIM dizini sorgulanır (`LS`), oluşturulan yeni MP4 video dosyası gerçek kamera listesinden otomatik keşfedilip Kamera Kayıtları listesine aktarılır (`[REC] new media discovered: <filename>`).
- **Kamera Kayıtlarında Yerel Arama & Sıralama**:
  - **Arama**: Dosya ve klasör isimlerine göre (örn: `FILE4089`, `EMRG`, `113MEDIA`, `116MEDIA`) harf büyüklüğüne duyarsız (case-insensitive) anlık arama.
  - **Sıralama**:
    - Tarih: Yeniden Eskiye (Varsayılan)
    - Tarih: Eskiden Yeniye
    - İsim: A → Z
    - İsim: Z → A
    - Klasör: A → Z
- **Gelişmiş Filtreleme & Dinamik Klasör Süzgeçleri**:
  - Yatay kaydırılabilir responsive çip düzeni ile **Kategori** (Tümü, Videolar, Fotoğraflar, Acil Durum, Telefona İndirilenler), **Lokasyon** (Kamerada, Telefonda) ve **Klasör** (dinamik algılanan `113MEDIA`, `116MEDIA` vb.) süzgeçleri.
  - **Sonuç Özeti**: Filtreleme ve arama yapıldığında `"143 kayıttan 18 tanesi gösteriliyor"` şeklinde dinamik bildirim.
- **Esnek ve Responsive Arayüz Düzenlemeleri**:
  - Dar ekranlı telefonlarda video ve fotoğraf izleme pencerelerindeki eylem butonları (`Kaydet`, `Aç`, `Paylaş`) metin kırpılması yaşamadan tek satırda (`maxLines = 1`, `softWrap = false`) hizalanır.
  - Medya kartlarındaki `Kamerada` ve `Telefonda` durum rozetleri alt satıra kaymadan tek satır rozet çipi olarak görüntülenir.
  - Bağlantı ekranındaki `SanJet DR38AS (ASUS RECO Smart)` model bilgisi responsive dikey key-value düzeninde sunulur.
- **Araç Modu & Bağlantı Gösterge Paneli (Vehicle Mode)**:
  - Kamera ağına bağlandığında donanım durumunu sorgular.
  - Kamera zaten kayıt yapıyorsa mükerrer komut göndermez, boştaysa otomatik kaydı başlatır.
- **Çift Çalışma Modu (Dual Mode)**:
  - **Simülasyon (Mock) Modu**: %100 çevrimdışı simülasyon.
  - **Gerçek Kamera Modu**: `192.168.42.1` TCP 7878 / 8787 ve RTSP canlı akış.

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

## 🚧 Donanım Doğrulama Durumu (Field Test RC7.1)

| Katman | Durum |
| :--- | :--- |
| **Simülasyon / Mock Modu** | ✅ `VERIFIED` (11/11 Self-Test Passed) |
| **Birim Test Paketi** | ✅ `VERIFIED` (57/57 Unit Tests Passed) |
| **Android Kod Tabanı & Derleme** | ✅ `VERIFIED` (Clean Build RC7.1) |
| **Fiziksel CR38 Donanım Doğrulaması** | ✅ `FIELD TEST RC7.1 VERIFIED` (Saha Testleri ve Donanım Doğrulandı) |

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
gradlew.bat test

:: Debug APK paketini oluşturma
gradlew.bat assembleDebug
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

## ✨ Features (Field Test RC7.1)

- **Recording State Navigation Persistence**:
  - The camera's recording state is managed at the `CameraRepository` level as authoritative device/session state.
  - Tab navigation does not trigger automatic `RECORD_STOP` or `RECORD_START` commands.
  - Re-entering Live Preview immediately displays the active red "RECORDING" indicator.
  - Preview initialization is idempotent and serialized to prevent duplicate `RESET_TO_VF` operations.
- **Safe Recording Completion & Instant File Discovery**:
  - After sending `RECORD_STOP`, waits for acknowledgement and a brief filesystem stabilization window (~500–1000 ms).
  - Queries DCIM directory (`LS`) while preserving the connection/token, automatically discovering newly created MP4 files (`[REC] new media discovered: <filename>`).
- **Camera Records Local Search & Sorting**:
  - **Search**: Fast case-insensitive search by filename and folder name (e.g., `FILE4089`, `EMRG`, `113MEDIA`, `116MEDIA`).
  - **Sorting Modes**: Date (Newest to Oldest default, Oldest to Newest), Name (A-Z, Z-A), Folder (A-Z).
- **Advanced Filters & Result Summary**:
  - Horizontally scrollable chips for **Category** (All, Videos, Photos, Emergency, Downloaded), **Storage** (On Camera, On Phone), and dynamic **Folder** filters (`113MEDIA`, `116MEDIA`).
  - Displays dynamic result summary (e.g., `"Showing 18 of 143 items"`).
- **Responsive UI Polish**:
  - Media player action buttons (`Kaydet`, `Aç`, `Paylaş`) remain single-line without vertical text wrapping on narrow device widths.
  - Status badges (`Kamerada`, `Telefonda`) remain compact single-line chips (`maxLines = 1`, `softWrap = false`).
  - Connection screen `SanJet DR38AS (ASUS RECO Smart)` model text uses responsive vertical key/value layout.

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
