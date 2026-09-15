package com.asus.recosmart.data.protocol

import com.asus.recosmart.domain.model.CameraResponse
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

            CameraResponse(
                msgId = msgId,
                rval = rval,
                token = extractedToken,
                param = paramStr,
                type = typeStr,
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

    fun parseListing(rawJson: String, defaultFolder: String = "100MEDIA"): List<com.asus.recosmart.domain.model.CameraFile> {
        val fileList = mutableListOf<com.asus.recosmart.domain.model.CameraFile>()
        try {
            val json = JSONObject(rawJson)
            if (json.has("listing")) {
                val array = json.getJSONArray("listing")
                for (i in 0 until array.length()) {
                    val itemObject = array.getJSONObject(i)
                    val names = itemObject.names() ?: continue
                    if (names.length() > 0) {
                        val rawName = names.getString(0)
                        val valueStr = itemObject.optString(rawName, "")

                        var folder = defaultFolder
                        var filename = rawName
                        if (rawName.contains("/")) {
                            val parts = rawName.split("/")
                            folder = parts[0]
                            filename = parts.getOrElse(1) { rawName }
                        }

                        var sizeBytes = 0L
                        var dateStr = valueStr
                        if (valueStr.contains("|")) {
                            val vParts = valueStr.split("|")
                            val sizePart = vParts[0].replace(Regex("[^0-9]"), "")
                            sizeBytes = sizePart.toLongOrNull() ?: 0L
                            dateStr = vParts.getOrElse(1) { valueStr }
                        }

                        fileList.add(com.asus.recosmart.domain.model.CameraFile(filename, folder, sizeBytes, dateStr))
                    }
                }
            }
        } catch (e: Exception) {
            // Keep empty list on parse failure
        }
        return fileList
    }
}
