package com.asus.recosmart.data.protocol

import com.asus.recosmart.domain.model.CameraDirectory
import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.domain.model.CameraResponse
import com.asus.recosmart.domain.model.CameraSetting
import org.json.JSONArray
import org.json.JSONObject

object ResponseParser {

    fun parse(rawJson: String): CameraResponse {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return CameraResponse(
                msgId = -1,
                rval = ReturnCode.COMMAND_FAILED.code,
                rawResponse = rawJson
            )
        }

        return try {
            val json = JSONObject(trimmed)
            val msgId = json.optInt("msg_id", -1)
            val rval = json.optInt("rval", -1)
            
            var extractedToken = json.optInt("token", 0)
            var paramStr: String? = null

            if (json.has("param")) {
                val paramObj = json.get("param")
                paramStr = paramObj.toString()

                if (msgId == 257) { // START_SESSION
                    when (paramObj) {
                        is Int -> extractedToken = paramObj
                        is String -> extractedToken = paramObj.toIntOrNull() ?: extractedToken
                        is JSONArray -> {
                            if (paramObj.length() > 0) {
                                extractedToken = paramObj.optInt(0, paramObj.optString(0).toIntOrNull() ?: extractedToken)
                            }
                        }
                    }
                }
            }

            val typeStr = if (json.has("type")) json.optString("type") else null
            val brandStr = if (json.has("brand")) json.optString("brand") else null
            val modelStr = if (json.has("model")) json.optString("model") else null
            val apiVerStr = if (json.has("api_ver")) json.optString("api_ver") else null
            val fwVerStr = if (json.has("fw_ver")) json.optString("fw_ver") else null
            val appTypeStr = if (json.has("app_type")) json.optString("app_type") else null
            val logoStr = if (json.has("logo")) json.optString("logo") else null
            val chipStr = if (json.has("chip")) json.optString("chip") else null
            val httpStr = if (json.has("http")) json.optString("http") else null

            CameraResponse(
                msgId = msgId,
                rval = rval,
                token = extractedToken,
                param = paramStr,
                type = typeStr,
                brand = brandStr,
                model = modelStr,
                apiVer = apiVerStr,
                fwVer = fwVerStr,
                appType = appTypeStr,
                logo = logoStr,
                chip = chipStr,
                http = httpStr,
                rawResponse = trimmed
            )
        } catch (e: Exception) {
            CameraResponse(
                msgId = -1,
                rval = ReturnCode.UNKNOWN.code,
                rawResponse = "Parse error [${e.localizedMessage}]: $trimmed"
            )
        }
    }

    fun parseSettings(rawJson: String): List<CameraSetting> {
        val settings = mutableListOf<CameraSetting>()
        try {
            val json = JSONObject(rawJson)
            if (json.has("param")) {
                val paramObj = json.get("param")
                if (paramObj is JSONArray) {
                    for (i in 0 until paramObj.length()) {
                        val obj = paramObj.optJSONObject(i) ?: continue
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = obj.optString(key, "")
                            settings.add(CameraSetting(key = key, value = value, label = key))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Defensive return on parse failure
        }
        return settings
    }

    fun parseDirectories(rawJson: String): List<CameraDirectory> {
        val dirs = mutableListOf<CameraDirectory>()
        try {
            val json = JSONObject(rawJson)
            if (json.has("listing")) {
                val array = json.getJSONArray("listing")
                for (i in 0 until array.length()) {
                    val itemObject = array.optJSONObject(i) ?: continue
                    val names = itemObject.names() ?: continue
                    if (names.length() > 0) {
                        val rawName = names.getString(0)
                        val timestamp = itemObject.optString(rawName, "")
                        if (rawName.endsWith("/") || timestamp.equals("dir", ignoreCase = true)) {
                            val dirName = rawName.trimEnd('/')
                            if (dirName.isNotEmpty()) {
                                dirs.add(
                                    CameraDirectory(
                                        name = dirName,
                                        remotePath = "/tmp/fuse_d/DCIM/$dirName",
                                        timestamp = timestamp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty list on parse failure
        }
        return dirs
    }

    fun parseFilesListing(rawJson: String, folderName: String): List<CameraFile> {
        val rawFiles = mutableListOf<CameraFile>()
        try {
            val json = JSONObject(rawJson)
            if (json.has("listing")) {
                val array = json.getJSONArray("listing")
                for (i in 0 until array.length()) {
                    val itemObject = array.optJSONObject(i)
                    if (itemObject != null) {
                        val names = itemObject.names() ?: continue
                        if (names.length() > 0) {
                            val rawName = names.getString(0)
                            val valueStr = itemObject.optString(rawName, "")

                            // Ignore directory entries inside subfolder listing
                            if (rawName.endsWith("/") || valueStr.equals("dir", ignoreCase = true)) {
                                continue
                            }

                            val filename = if (rawName.contains("/")) rawName.substringAfterLast("/") else rawName
                            var sizeBytes = 0L
                            var dateStr = valueStr

                            if (valueStr.contains("|")) {
                                val vParts = valueStr.split("|")
                                val sizePart = vParts[0].replace(Regex("[^0-9]"), "")
                                sizeBytes = sizePart.toLongOrNull() ?: 0L
                                dateStr = vParts.getOrElse(1) { valueStr }
                            } else {
                                val digitsOnly = valueStr.replace(Regex("[^0-9]"), "")
                                if (digitsOnly.isNotEmpty() && !valueStr.contains("-") && !valueStr.contains(":")) {
                                    sizeBytes = digitsOnly.toLongOrNull() ?: 0L
                                }
                            }

                            rawFiles.add(
                                CameraFile(
                                    filename = filename,
                                    folder = folderName,
                                    sizeBytes = sizeBytes,
                                    dateTime = dateStr,
                                    isDirectory = false,
                                    remotePath = "/tmp/fuse_d/DCIM/$folderName/$filename"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty list on parse failure
        }
        return pairFiles(rawFiles)
    }

    fun pairFiles(rawFiles: List<CameraFile>): List<CameraFile> {
        val thumbnailsByBase = rawFiles.filter { it.isThumbnail }
            .associateBy { it.baseRecordingName }

        val pairedList = mutableListOf<CameraFile>()

        for (file in rawFiles) {
            if (file.isThumbnail) {
                // Companion thumbnail files are paired with main file and excluded from top-level list
                continue
            }
            val companionThumb = thumbnailsByBase[file.baseRecordingName]
            val finalFile = if (companionThumb != null) {
                file.copy(thumbnailUrl = companionThumb.httpUrl)
            } else {
                file
            }
            pairedList.add(finalFile)
        }

        return pairedList
    }

    fun parseListing(rawJson: String, defaultFolder: String = "100MEDIA"): List<CameraFile> {
        val dirs = parseDirectories(rawJson)
        if (dirs.isNotEmpty()) {
            return dirs.map { dir ->
                CameraFile(
                    filename = dir.name,
                    folder = dir.name,
                    sizeBytes = 0L,
                    dateTime = dir.timestamp,
                    isDirectory = true,
                    remotePath = dir.remotePath
                )
            }
        }
        return parseFilesListing(rawJson, defaultFolder)
    }
}
