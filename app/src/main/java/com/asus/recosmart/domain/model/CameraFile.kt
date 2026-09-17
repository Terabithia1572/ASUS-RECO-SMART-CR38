package com.asus.recosmart.domain.model

data class CameraFile(
    val filename: String,
    val folder: String = "116MEDIA",
    val sizeBytes: Long = 0,
    val dateTime: String = "",
    val isDirectory: Boolean = false,
    val remotePath: String = "/tmp/fuse_d/DCIM/$folder/$filename",
    val thumbnailUrl: String? = null
) {
    val fullCameraPath: String
        get() = remotePath

    val remoteIdentity: String
        get() = "$folder/$filename"

    val httpUrl: String
        get() = CameraMediaUrlResolver.resolve(folder, filename)

    val isVideo: Boolean
        get() = filename.endsWith(".MP4", ignoreCase = true) || filename.endsWith(".MOV", ignoreCase = true)

    val isPhoto: Boolean
        get() = filename.endsWith(".JPG", ignoreCase = true) || filename.endsWith(".JPEG", ignoreCase = true)

    val isEmergency: Boolean
        get() = filename.startsWith("EMRG", ignoreCase = true)

    val isThumbnail: Boolean
        get() = filename.contains("_thm", ignoreCase = true)

    val baseRecordingName: String
        get() {
            val nameWithoutExt = filename.substringBeforeLast(".")
            return if (nameWithoutExt.endsWith("_thm", ignoreCase = true)) {
                nameWithoutExt.substringBeforeLast("_thm")
            } else {
                nameWithoutExt
            }
        }

    val formattedSize: String
        get() {
            if (isDirectory) return "<DIR>"
            if (sizeBytes <= 0) return "Bilinmiyor"
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }

    val mediaTypeLabel: String
        get() = when {
            isDirectory -> "Dizin"
            isEmergency -> "Acil Durum Kaydı"
            isVideo -> "Video"
            isPhoto -> "Fotoğraf"
            else -> "Dosya"
        }
}
