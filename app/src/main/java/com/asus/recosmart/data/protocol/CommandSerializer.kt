package com.asus.recosmart.data.protocol

import com.asus.recosmart.domain.model.CameraCommand
import org.json.JSONObject

object CommandSerializer {

    fun serialize(command: CameraCommand, sessionToken: Int): String {
        val json = JSONObject()
        json.put("msg_id", command.msgId)

        // START_SESSION initial token is always 0 in original ASUS JSonBuilder
        if (command is CameraCommand.StartSession) {
            json.put("token", 0)
        } else {
            json.put("token", sessionToken)
        }

        when (command) {
            is CameraCommand.GetSetting -> {
                json.put("type", command.paramKey)
            }
            is CameraCommand.GetAppStatus -> {
                json.put("type", "app_status")
            }
            is CameraCommand.SetSetting -> {
                json.put("type", command.paramKey)
                json.put("param", command.paramValue)
            }
            is CameraCommand.ResetToVf -> {
                json.put("param", "force")
            }
            is CameraCommand.ListFiles -> {
                if (command.path.isNotEmpty()) {
                    json.put("param", command.path)
                }
            }
            is CameraCommand.ChangeDir -> {
                json.put("param", command.path)
            }
            is CameraCommand.DeleteFile -> {
                json.put("param", command.filePath)
            }
            is CameraCommand.CustomCommand -> {
                if (!command.rawParam.isNullOrEmpty()) {
                    json.put("param", command.rawParam)
                }
            }
            else -> {
                // No extra params required for RecordStart, RecordStop, TakePhoto, Format, StopSession, etc.
            }
        }

        return json.toString()
    }
}
