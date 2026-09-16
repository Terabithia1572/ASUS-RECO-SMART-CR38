package com.asus.recosmart.domain.model

data class CameraStatus(
    val isConnected: Boolean = false,
    val isRecording: Boolean = false,
    val isTimelapseActive: Boolean = false,
    val isBurstActive: Boolean = false,
    val batteryLevel: Int = 100, // 0 - 100
    val sdCardStatus: SdCardStatus = SdCardStatus.READY,
    val sdCardFreeSpaceMb: Long = 16384,
    val activeToken: Int = 0,
    val cameraIp: String = DEFAULT_CAMERA_IP,
    val commandPort: Int = DEFAULT_COMMAND_PORT,
    val dataPort: Int = DEFAULT_DATA_PORT,
    val rtspUrl: String = DEFAULT_RTSP_URL,
    val currentMode: CameraMode = CameraMode.VIDEO,
    val brand: String = "ASUS",
    val model: String = "RECO Smart CR38",
    val firmwareVersion: String = "",
    val apiVersion: String = "",
    val firstVideoFrameRendered: Boolean = false
) {
    companion object {
        const val DEFAULT_CAMERA_IP = "192.168.42.1"
        const val DEFAULT_COMMAND_PORT = 7878
        const val DEFAULT_DATA_PORT = 8787
        const val DEFAULT_RTSP_URL = "rtsp://192.168.42.1/live"
        const val DEFAULT_DCIM_PATH = "/tmp/fuse_d/DCIM/"
        const val DEFAULT_HTTP_BASE_URL = "http://192.168.42.1/DCIM/"
    }
}

enum class SdCardStatus {
    READY,
    NO_CARD,
    CARD_FULL,
    NEEDS_FORMAT,
    ERROR
}

enum class CameraMode {
    VIDEO,
    PHOTO,
    TIMELAPSE,
    BURST
}
