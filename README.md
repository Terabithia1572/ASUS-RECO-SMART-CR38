# ASUS RECO Smart / CR38 Android Client

An unofficial Android client and field diagnostic application for the ASUS RECO Smart (CR38) Dash Cam & Action Camera.

> **Disclaimer:** This project is not affiliated with or endorsed by ASUS.

---

## 📌 Purpose

This application provides remote control, live stream monitoring, file browsing, configuration management, and low-level protocol diagnostics for the ASUS RECO Smart / CR38 camera over its local Wi-Fi access point network.

---

## 🛠 Features

- **Dual Operating Modes**:
  - **Mock Camera Simulator**: Full offline simulation mode for UI testing, camera state transitions, photo taking, video recording, settings customization, and file browsing without physical hardware.
  - **Real Camera Hardware Mode**: Socket-bound direct communication targeting the CR38 access point network with custom Android Wi-Fi network routing.
- **TCP Protocol Engine**: JSON-over-TCP request/response framing with single-command lock serialization, async event handling, dynamic socket binding, and automatic token management.
- **RTSP Live View**: RTSP video player integration (`rtsp://192.168.42.1/live`) backed by AndroidX Media3 / ExoPlayer.
- **Storage & DCIM Browser**: Camera DCIM index listing (`/tmp/fuse_d/DCIM/`), thumbnail preview state, and file operations with explicit deletion safety checks.
- **Camera Settings System**: Interactive camera settings manager for resolution, loop recording, EV, G-sensor, Wi-Fi SSID/password, and system configuration.
- **Protocol Console & Diagnostics**: Dedicated technical log viewer with full raw TX/RX JSON inspection, hardware connection diagnostic test suite, and one-tap diagnostic field report exporter.

---

## 🌐 Default Camera Network Parameters

| Parameter | Default Value |
| :--- | :--- |
| **Camera IP** | `192.168.42.1` |
| **Command TCP Port** | `7878` |
| **Data TCP Port** | `8787` |
| **RTSP Live Stream** | `rtsp://192.168.42.1/live` |
| **DCIM Root Path** | `/tmp/fuse_d/DCIM/` |

---

## 🧪 Status & Field Validation

- **Current Status**: **Release Candidate 1 (`1.0.0-rc1`)**
- **Hardware Verification**: Physical CR38 protocol validation is still in progress.
- **Mock Self-Test Regression**: 11/11 PASSED.

---

## 📱 Build & Test

### Environment Requirements
- JDK 17+ (Android Studio JBR recommended)
- Android SDK 34+
- Gradle 8.x

### Build Commands
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb -s <DEVICE_ID> install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License & Attribution

Developed for field testing and reverse-engineering analysis of ASUS RECO Smart / CR38 device protocols.
