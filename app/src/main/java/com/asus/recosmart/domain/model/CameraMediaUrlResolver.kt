package com.asus.recosmart.domain.model

import java.net.URLEncoder

/**
 * Authoritative resolver for ASUS RECO Smart CR38 camera HTTP media URLs.
 * Centralizes all HTTP URL construction for video streaming, photo preview,
 * media downloads, sharing, and companion thumbnails.
 *
 * Guarantees case preservation for folder and filename, and handles safe URL encoding.
 */
object CameraMediaUrlResolver {

    /**
     * Resolves the authoritative HTTP URL for a given camera media file.
     *
     * @param folder Camera DCIM folder name (e.g., "105MEDIA", "116MEDIA", "110MEDIA")
     * @param filename Exact camera filename (e.g., "FILE0501.JPG", "FILE1001.mp4", "EMRG3992.mp4")
     * @param cameraIp Camera IP address, defaulting to 192.168.42.1
     * @param httpBasePath HTTP base directory on camera, defaulting to "/DCIM/"
     * @return Complete HTTP URL string
     */
    fun resolve(
        folder: String,
        filename: String,
        cameraIp: String = CameraStatus.DEFAULT_CAMERA_IP,
        httpBasePath: String = "/DCIM/"
    ): String {
        val cleanFolder = folder.trim().trim('/')
        val cleanFilename = filename.trim().trim('/')
        val cleanBasePath = if (httpBasePath.endsWith("/")) httpBasePath else "$httpBasePath/"

        // Preserve exact case of folder and filename
        val encodedFolder = encodePathComponent(cleanFolder)
        val encodedFilename = encodePathComponent(cleanFilename)

        return "http://$cameraIp$cleanBasePath$encodedFolder/$encodedFilename"
    }

    /**
     * Helper to resolve HTTP URL for a CameraFile domain model.
     */
    fun resolve(file: CameraFile, cameraIp: String = CameraStatus.DEFAULT_CAMERA_IP): String {
        return resolve(file.folder, file.filename, cameraIp)
    }

    /**
     * Encodes a single path component while preserving case and allowed characters.
     */
    private fun encodePathComponent(component: String): String {
        return try {
            // URLEncoder encodes spaces to '+' by default; convert '+' back to '%20' for strict URL paths
            URLEncoder.encode(component, "UTF-8")
                .replace("+", "%20")
                .replace("%2F", "/") // Preserve any literal slash if present
        } catch (e: Exception) {
            component
        }
    }

    /**
     * Formats structured diagnostic log text for media operations.
     */
    fun formatDiagnosticLog(
        operation: String,
        file: CameraFile,
        httpUrl: String,
        statusCode: Int? = null
    ): String {
        val statusStr = statusCode?.let { "HTTP status=$it" } ?: "HTTP status=PENDING"
        return "[MEDIA] filename=${file.filename} | folder=${file.folder} | cameraPath=${file.remotePath} | httpUrl=$httpUrl | operation=$operation | $statusStr"
    }
}
