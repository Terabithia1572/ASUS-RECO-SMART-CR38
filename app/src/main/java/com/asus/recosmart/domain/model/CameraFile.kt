package com.asus.recosmart.domain.model

data class CameraFile(
    val filename: String,
    val folder: String = "100MEDIA",
    val sizeBytes: Long = 0,
    val dateTime: String = "",
    val isDirectory: Boolean = false,
    val fullCameraPath: String = "/tmp/fuse_d/DCIM/$folder/$filename"
) {
    val httpUrl: String
        get() = "http://${CameraStatus.DEFAULT_CAMERA_IP}/DCIM/${folder}/${filename}"

    val formattedSize: String
        get() {
            if (isDirectory) return "<DIR>"
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }

    val isVideo: Boolean
        get() = filename.endsWith(".MP4", ignoreCase = true) || filename.endsWith(".MOV", ignoreCase = true)

    val isPhoto: Boolean
        get() = filename.endsWith(".JPG", ignoreCase = true) || filename.endsWith(".JPEG", ignoreCase = true)
}
