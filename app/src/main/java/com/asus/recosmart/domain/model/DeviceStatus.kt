package com.asus.recosmart.domain.model

enum class DeviceStatus(val wireName: String) {
    IDLE("idle"),
    VF("vf"),
    RECORD("record"),
    CAPTURE("capture"),
    UVC("uvc"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): DeviceStatus {
            if (value.isNullOrEmpty()) return IDLE
            return values().find { it.wireName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

data class DeviceInformation(
    val brand: String = "ASUS",
    val model: String = "CR38",
    val firmwareVersion: String = "1.0.0_CR38",
    val applicationType: String = "Dashcam"
)
