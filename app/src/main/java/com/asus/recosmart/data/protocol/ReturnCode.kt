package com.asus.recosmart.data.protocol

enum class ReturnCode(val code: Int, val description: String) {
    SUCCESS(0, "Success"),
    SESSION_START_FAIL(-1, "Failed to start session"),
    INVALID_TOKEN(-3, "Invalid session token"),
    COMMAND_FAILED(-4, "Command execution failed"),
    CAMERA_BUSY(-5, "Camera is busy"),
    OUT_OF_MEMORY(-7, "Device rejection / Operation not permitted in current state (rval=-7)"),
    NO_SD_CARD(-9, "No SD card inserted"),
    INVALID_PARAM(-13, "Invalid parameter"),
    UNKNOWN(-999, "Unknown error");

    companion object {
        fun fromCode(code: Int): ReturnCode {
            return values().firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}
