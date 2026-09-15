package com.asus.recosmart.domain.model

/**
 * Clean domain representation of ASUS RECO Smart / CR38 TCP commands.
 * Exact command msg_id values derived from original decompiled com.sanjet.communication.v2.Command.
 */
sealed class CameraCommand(val msgId: Int, val commandName: String) {
    object StartSession : CameraCommand(257, "START_SESSION")
    object StopSession : CameraCommand(258, "STOP_SESSION")
    object ResetToVf : CameraCommand(259, "RESET_TO_VF")
    object StopVf : CameraCommand(260, "STOP_VF")

    object RecordStart : CameraCommand(513, "RECORD_START")
    object RecordStop : CameraCommand(514, "RECORD_STOP")

    object TakePhoto : CameraCommand(769, "TAKE_PHOTO")

    data class GetSetting(val paramKey: String) : CameraCommand(1, "GET_SETTING")
    data class SetSetting(val paramKey: String, val paramValue: String) : CameraCommand(2, "SET_SETTING")
    object GetAllCurrentSettings : CameraCommand(3, "GET_ALL_CURRENT_SETTINGS")
    object FormatSdCard : CameraCommand(4, "FORMAT")

    data class DeleteFile(val filePath: String) : CameraCommand(1281, "DEL_FILE")
    data class ListFiles(val path: String = "/tmp/fuse_d/DCIM/") : CameraCommand(1282, "LS")
    data class ChangeDir(val path: String) : CameraCommand(1283, "CD")

    object GetDeviceInfo : CameraCommand(11, "GET_DEVICE_INFORMATION")
    object PhotoPiv : CameraCommand(53270, "PHOTO_PIV")
    object GetAppStatus : CameraCommand(1, "GET_APP_STATUS")

    data class CustomCommand(val customMsgId: Int, val name: String, val rawParam: String? = null) : CameraCommand(customMsgId, name)
}
