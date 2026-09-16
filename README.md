# ASUS RECO Smart / CR38 Android Client

> **Türkçe:** Bu proje ASUS RECO Smart / CR38 kamera için geliştirilen bağımsız ve resmi olmayan bir Android istemcisi ve protokol araştırma projesidir.  
> **English:** This project is an independent, unofficial Android client and protocol research application for the ASUS RECO Smart / CR38 camera.

---

# 🇹🇷 Türkçe

## 📱 Proje Hakkında

**ASUS RECO Smart / CR38 Android İstemcisi**, ASUS RECO Smart (CR38 model / SanJet DR38AS) araç ve aksiyon kamerasının Wi-Fi erişim noktası (Access Point) ağı üzerinden uzaktan kontrol edilmesini, canlı yayın izlenmesini, araç modu otomasyonunu, dosya yönetimini ve TCP komut protokolü hata ayıklamasını sağlayan bağımsız bir Android uygulamasıdır.

Orijinal mobil uygulamanın eski Android sürümlerine bağımlılığını ortadan kaldırmak ve kameranın donanım protokolünü belgelemek amacıyla modern Android mimarisi (**Kotlin**, **Jetpack Compose**, **MVVM**, **Coroutines**, **Media3/ExoPlayer**) kullanılarak sıfırdan geliştirilmiştir.

---

## ✨ Özellikler (Field Test RC7)

- **Araç Modu & Bağlantı Gösterge Paneli (Vehicle Mode)**:
  - Kamera ağına bağlandığında `getAppStatus()` ile donanım durumunu sorgular.
  - Kamera zaten kayıt yapıyorsa mükerrer komut göndermez, `"Karıt Yapıyor"` olarak bildirir.
  - Kamera boştaysa varsayılan tercihe göre otomatik kaydı başlatır.
  - Bağlantı ekranında Wi-Fi ağ yardımcısı (`Settings.ACTION_WIFI_SETTINGS`) ve donanım gösterge paneli içerir.
- **Çift Çalışma Modu (Dual Mode)**:
  - **Simülasyon (Mock) Modu**: Donanım bağlantısı olmadan oturum başlatma, fotoğraf/video çekimi, ayar değiştirme ve SD kart dosya gezintisini %100 çevrimdışı simüle eder.
  - **Gerçek Kamera Modu**: Kameranın `192.168.42.1` Wi-Fi erişim ağına TCP soket seviyesinde doğrudan bağlanır.
- **Fotoğraf Çözünürlüğü Gerçekliği & Meta Veri Doğrulama**:
  - Kamera wire ayarı (örn. 16 MP) ile fiziksel donanım JPEG çıktısı (1920×1080 ~2.1 MP) arasındaki farkı açıklar.
  - Bellek tüketmeden `inJustDecodeBounds` ile indirilen görsellerin gerçek piksel çözünürlüğünü ayrıştırır.
- **Kategori Bazlı Ayarlar & Kamera Saati Eşitleme**:
  - Ayarları **Video**, **Fotoğraf**, **Güvenlik ve Sürüş**, **Görüntü** ve **Sistem** başlıklarına ayırır.
  - `"Kamera Saatini Telefonla Eşitle"` butonu ile kameranın tarih/saatini telefon zamanıyla günceller.
- **Medya Yöneticisi Rozetleri ve İndirilenler Filtresi**:
  - Dosyalar için `"Telefonda"` ve `"Kamerada"` durum rozetleri.
  - `Telefona İndirilenler` filtresi ile MediaStore'a kaydedilen dosyaları anında süzme.
- **Protokol Hata Ayıklama Konsolu & Arındırılmış Rapor Kopyalama**:
  - Ham TX/RX JSON paketlerinin izlenmesi.
  - `"Tanılama Raporunu Kopyala"` butonu ile hassas cihaz kimlikleri temizlenmiş teknik tanılama raporunu panoya aktarma.
- **Özgün Adaptive Uygulama İkonu**:
  - Lacivert zemin ve siyan kamera merceği temalı yeni vektör launcher ikonu.

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

## 🚧 Donanım Doğrulama Durumu (Field Test RC7)

| Katman | Durum |
| :--- | :--- |
| **Simülasyon / Mock Modu** | ✅ `VERIFIED` (11/11 Self-Test Passed) |
| **Birim Test Paketi** | ✅ `VERIFIED` (42/42 Unit Tests Passed) |
| **Android Kod Tabanı & Derleme** | ✅ `VERIFIED` (Clean Build RC7) |
| **Fiziksel CR38 Donanım Doğrulaması** | ✅ `FIELD TEST RC7 VERIFIED` (Saha Testi ve Donanım Doğrulandı) |

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

## ✨ Features (Field Test RC7)

- **Vehicle Mode Automation & Dashboard**:
  - Automatically queries device status (`getAppStatus()`) upon connection.
  - Displays `"Recording"` without duplicate commands if already recording.
  - Auto-triggers single `RECORD_START` if camera is idle according to user preference.
  - Includes Wi-Fi connection helper button (`Settings.ACTION_WIFI_SETTINGS`).
- **Photo Resolution Truthfulness & Metadata Extraction**:
  - Explains hardware JPEG output dimensions (~1920x1080 ~2.1 MP) vs wire setting values.
  - Extracts exact pixel dimensions via `inJustDecodeBounds` without memory allocation.
- **Categorized Settings & Camera Clock Sync**:
  - Organized into **Video**, **Photo**, **Security**, **Image**, and **System** categories.
  - Includes `"Sync Camera Clock to Phone"` feature.
- **Media Manager Badges & Downloaded Filter**:
  - `"On Phone"` and `"On Camera"` status badges.
  - Filter category for `Downloaded to Phone`.
- **Diagnostics & Sanitized Report Copy**:
  - Copy sanitized diagnostic reports to clipboard with redacted MAC addresses.
- **Original Vector Adaptive Launcher Icon**:
  - Navy background and cyan lens motif.

---

## 🚀 Building & Setup

```bash
# Run unit tests (Windows)
gradlew.bat test

# Assemble debug APK
gradlew.bat assembleDebug
```

---

## ⚖️ Legal Disclaimer

This project is an independent research implementation.  
**ASUS and RECO Smart are registered trademarks of ASUSTeK Computer Inc. This application is an independent community project.**
