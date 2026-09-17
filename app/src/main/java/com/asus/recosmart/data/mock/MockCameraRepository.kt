package com.asus.recosmart.data.mock

import com.asus.recosmart.data.protocol.ResponseParser
import com.asus.recosmart.data.protocol.ReturnCode
import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.domain.model.CameraResponse
import com.asus.recosmart.domain.model.CameraSetting
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.model.SdCardStatus
import com.asus.recosmart.domain.model.SessionState
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MockCameraRepository : CameraRepository {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _cameraStatus = MutableStateFlow(CameraStatus())
    override val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()

    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    override val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    private val _isMockMode = MutableStateFlow(true)
    override val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private val mockSettings = mutableMapOf(
        "video_resolution" to CameraSetting("video_resolution", "1920x1080 30P 16:9", "Video Resolution"),
        "photo_size" to CameraSetting("photo_size", "16M (4608x3456 4:3)", "Photo Resolution"),
        "rec mode" to CameraSetting("rec mode", "continuous", "Record Mode"),
        "loop record" to CameraSetting("loop record", "LOOP_REC_3_MIN", "Loop Recording"),
        "gs sense" to CameraSetting("gs sense", "medium", "G-Sensor Sensitivity"),
        "photo burst" to CameraSetting("photo burst", "5p1s", "Photo Burst Configuration"),
        "photo time lapse" to CameraSetting("photo time lapse", "1ps", "Time Lapse Interval"),
        "ev value" to CameraSetting("ev value", "0.0ev", "Exposure Compensation (EV)"),
        "image rotate" to CameraSetting("image rotate", "NORMAL", "Image Orientation"),
        "sys mode" to CameraSetting("sys mode", "CAR_MODE", "Camera Operational Mode"),
        "language" to CameraSetting("language", "ENG", "System Language"),
        "microphone" to CameraSetting("microphone", "on", "Microphone Audio"),
        "auto_power_off" to CameraSetting("auto_power_off", "5MIN", "Auto Power Off"),
        "wifi_ssid" to CameraSetting("wifi_ssid", "CR38_ASUS_9F82", "Wi-Fi SSID")
    )

    private val mockFiles = mutableListOf(
        CameraFile("FILE3956.mp4", "116MEDIA", 62914560L, "2015-08-23 15:50:40", thumbnailUrl = "http://192.168.42.1/DCIM/116MEDIA/FILE3956_thm.mp4"),
        CameraFile("EMRG3992.mp4", "116MEDIA", 62914560L, "2015-08-23 15:52:00"),
        CameraFile("FILE3957.mp4", "116MEDIA", 60123990L, "2015-08-23 16:01:10", thumbnailUrl = "http://192.168.42.1/DCIM/116MEDIA/FILE3957_thm.mp4"),
        CameraFile("FILE1001.mp4", "110MEDIA", 452001024L, "2021-07-11 15:50:40"),
        CameraFile("FILE0501.JPG", "105MEDIA", 3450112L, "2019-09-18 15:50:40")
    )

    init {
        log("[MOCK ENGINE] Initialized Mock Camera Repository for ASUS CR38")
    }

    override suspend fun connect(ip: String, port: Int): Result<Unit> {
        log("[MOCK TX] Connect -> $ip:$port")
        _sessionState.value = SessionState.Connecting
        delay(300)
        log("[MOCK RX] TCP Connection established with $ip:$port")
        val sessionRes = startSession()
        return if (sessionRes.isSuccess) {
            val token = sessionRes.getOrThrow()
            _cameraStatus.value = _cameraStatus.value.copy(
                isConnected = true,
                cameraIp = ip,
                commandPort = port,
                activeToken = token
            )
            Result.success(Unit)
        } else {
            val err = sessionRes.exceptionOrNull()?.localizedMessage ?: "Start session failed"
            _sessionState.value = SessionState.Error(err)
            Result.failure(Exception(err))
        }
    }

    override suspend fun disconnect() {
        log("[MOCK TX] Disconnect requested")
        _sessionState.value = SessionState.Disconnected
        _cameraStatus.value = _cameraStatus.value.copy(isConnected = false, activeToken = 0, isRecording = false)
        log("[MOCK RX] Camera session disconnected")
    }

    override suspend fun startSession(): Result<Int> {
        val simulatedToken = if (_cameraStatus.value.activeToken > 0) _cameraStatus.value.activeToken else 1001
        log("[MOCK TX] -> {\"msg_id\":257,\"token\":0}")
        delay(400)
        val jsonRx = "{\"rval\":0,\"msg_id\":257,\"param\":$simulatedToken}"
        log("[MOCK RX] <- $jsonRx")

        _sessionState.value = SessionState.Connected(simulatedToken)
        _cameraStatus.value = _cameraStatus.value.copy(isConnected = true, activeToken = simulatedToken)
        return Result.success(simulatedToken)
    }

    override suspend fun recoverSession(): Result<Int> {
        log("[MOCK RECOVERY] Recovering mock camera session...")
        return startSession()
    }

    override suspend fun startRecording(origin: String): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        log("[CMD][origin=$origin] [MOCK TX] -> {\"msg_id\":513,\"token\":$token}")
        delay(300)
        val response = CameraResponse(msgId = 513, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":513}")
        log("[MOCK RX] <- ${response.rawResponse}")
        _cameraStatus.value = _cameraStatus.value.copy(isRecording = true)
        return Result.success(response)
    }

    override suspend fun stopRecording(origin: String): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        log("[REC] stop requested")
        log("[CMD][origin=$origin] [MOCK TX] -> {\"msg_id\":514,\"token\":$token}")
        delay(300)
        val response = CameraResponse(msgId = 514, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":514}")
        log("[MOCK RX] <- ${response.rawResponse}")
        log("[REC] RECORD_STOP acknowledged")
        _cameraStatus.value = _cameraStatus.value.copy(isRecording = false)

        log("[REC] waiting for filesystem stabilization")
        delay(300)

        // Add a newly recorded mock file
        val nextId = mockFiles.size + 1
        val newFile = CameraFile("LOCA${String.format("%04d", nextId)}.MP4", "100MEDIA", 210000000L, getCurrentTimestamp())
        mockFiles.add(0, newFile)

        log("[REC] refreshing DCIM")
        log("[REC] new media discovered: ${newFile.filename}")

        return Result.success(response.copy(discoveredFile = newFile))
    }

    override suspend fun takePhoto(origin: String): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        log("[CMD][origin=$origin] [MOCK TX] -> {\"msg_id\":769,\"token\":$token}")
        delay(350)

        val nextId = mockFiles.size + 1
        val newFile = CameraFile("LOCA${String.format("%04d", nextId)}.JPG", "100MEDIA", 3800000L, getCurrentTimestamp())
        mockFiles.add(0, newFile)

        val response = CameraResponse(msgId = 769, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":769}", discoveredFile = newFile)
        log("[MOCK RX] <- ${response.rawResponse}")

        return Result.success(response)
    }

    override suspend fun fetchAllSettings(): Result<List<CameraSetting>> {
        val token = _cameraStatus.value.activeToken
        log("[MOCK TX] -> {\"msg_id\":3,\"token\":$token}")
        delay(300)
        log("[MOCK RX] <- {\"rval\":0,\"msg_id\":3,\"param\":{...}}")
        return Result.success(mockSettings.values.toList())
    }

    override suspend fun updateSetting(key: String, value: String): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        log("[MOCK TX] -> {\"msg_id\":2,\"token\":$token,\"type\":\"$key\",\"param\":\"$value\"}")
        delay(300)
        val existing = mockSettings[key]
        if (existing != null) {
            mockSettings[key] = existing.copy(value = value)
        } else {
            mockSettings[key] = CameraSetting(key, value, key)
        }
        val response = CameraResponse(msgId = 2, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":2}")
        log("[MOCK RX] <- ${response.rawResponse}")
        return Result.success(response)
    }

    override suspend fun listFiles(path: String): Result<List<CameraFile>> {
        val token = _cameraStatus.value.activeToken
        log("[MOCK TX] -> {\"msg_id\":1282,\"param\":\"$path\",\"token\":$token}")
        delay(400)
        log("[MOCK RX] <- {\"rval\":0,\"msg_id\":1282,\"listing\":[${mockFiles.size} items]}")
        return Result.success(mockFiles.toList())
    }

    override suspend fun deleteFile(filePath: String): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        log("[MOCK TX] -> {\"msg_id\":1281,\"param\":\"$filePath\",\"token\":$token}")
        delay(300)
        mockFiles.removeAll { it.fullCameraPath == filePath || it.filename == filePath }
        val response = CameraResponse(msgId = 1281, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":1281}")
        log("[MOCK RX] <- ${response.rawResponse}")
        return Result.success(response)
    }

    override suspend fun formatSdCard(): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        log("[MOCK TX] -> {\"msg_id\":4,\"token\":$token}")
        delay(800)
        mockFiles.clear()
        val response = CameraResponse(msgId = 4, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":4}")
        log("[MOCK RX] <- ${response.rawResponse}")
        _cameraStatus.value = _cameraStatus.value.copy(sdCardStatus = SdCardStatus.READY)
        return Result.success(response)
    }

    override suspend fun factoryReset(): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        log("[MOCK TX] -> {\"msg_id\":2,\"token\":$token,\"type\":\"factory default\",\"param\":\"on\"}")
        delay(500)
        mockSettings.clear()
        mockSettings.putAll(mapOf(
            "video_resolution" to CameraSetting("video_resolution", "1920x1080 30P 16:9", "Video Resolution"),
            "photo_size" to CameraSetting("photo_size", "16M (4608x3456 4:3)", "Photo Resolution"),
            "rec mode" to CameraSetting("rec mode", "continuous", "Record Mode"),
            "loop record" to CameraSetting("loop record", "LOOP_REC_3_MIN", "Loop Recording"),
            "gs sense" to CameraSetting("gs sense", "medium", "G-Sensor Sensitivity"),
            "photo burst" to CameraSetting("photo burst", "5p1s", "Photo Burst Configuration"),
            "photo time lapse" to CameraSetting("photo time lapse", "1ps", "Time Lapse Interval"),
            "ev value" to CameraSetting("ev value", "0.0ev", "Exposure Compensation (EV)"),
            "image rotate" to CameraSetting("image rotate", "NORMAL", "Image Orientation"),
            "sys mode" to CameraSetting("sys mode", "CAR_MODE", "Camera Operational Mode"),
            "language" to CameraSetting("language", "ENG", "System Language"),
            "microphone" to CameraSetting("microphone", "on", "Microphone Audio"),
            "auto_power_off" to CameraSetting("auto_power_off", "5MIN", "Auto Power Off"),
            "wifi_ssid" to CameraSetting("wifi_ssid", "CR38_ASUS_9F82", "Wi-Fi SSID")
        ))
        val response = CameraResponse(msgId = 2, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":2}")
        log("[MOCK RX] <- ${response.rawResponse}")
        return Result.success(response)
    }

    override suspend fun resetToVf(origin: String): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        if (_cameraStatus.value.isRecording) {
            log("[CMD][origin=$origin] RESET_TO_VF skipped because camera is currently RECORDING.")
            return Result.success(CameraResponse(msgId = 259, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":259}"))
        }
        log("[CMD][origin=$origin] [MOCK TX] -> {\"msg_id\":259,\"token\":$token,\"param\":\"force\"}")
        delay(300)
        val response = CameraResponse(msgId = 259, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":259}")
        log("[MOCK RX] <- ${response.rawResponse}")
        return Result.success(response)
    }

    override suspend fun prepareLiveView(origin: String): Result<CameraResponse> {
        return resetToVf(origin)
    }

    override suspend fun stopLiveView(origin: String): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        if (_cameraStatus.value.isRecording) {
            log("[CMD][origin=$origin] STOP_VF skipped because camera is currently RECORDING.")
            return Result.success(CameraResponse(msgId = 260, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":260}"))
        }
        log("[CMD][origin=$origin] [MOCK TX] -> {\"msg_id\":260,\"token\":$token}")
        delay(300)
        val response = CameraResponse(msgId = 260, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":260}")
        log("[MOCK RX] <- ${response.rawResponse}")
        return Result.success(response)
    }

    override suspend fun takePhotoPiv(): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        log("[MOCK TX] -> {\"msg_id\":53270,\"token\":$token}")
        delay(350)

        val nextId = mockFiles.size + 1
        val newFile = CameraFile("PIV_${String.format("%04d", nextId)}.JPG", "100MEDIA", 3900000L, getCurrentTimestamp())
        mockFiles.add(0, newFile)

        val response = CameraResponse(msgId = 53270, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":53270}", discoveredFile = newFile)
        log("[MOCK RX] <- ${response.rawResponse}")

        return Result.success(response)
    }

    override suspend fun getDeviceInformation(): Result<CameraResponse> {
        val token = _cameraStatus.value.activeToken
        log("[MOCK TX] -> {\"msg_id\":11,\"token\":$token}")
        delay(300)
        val jsonRx = "{\"rval\":0,\"msg_id\":11,\"brand\":\"ASUS\",\"model\":\"CR38\",\"fw_ver\":\"1.00.01\",\"applicationType\":\"CarCam\"}"
        log("[MOCK RX] <- $jsonRx")
        val response = CameraResponse(msgId = 11, rval = 0, token = token, rawResponse = jsonRx)
        return Result.success(response)
    }

    override suspend fun getAppStatus(): Result<com.asus.recosmart.domain.model.DeviceStatus> {
        val token = _cameraStatus.value.activeToken
        log("[MOCK TX] -> {\"msg_id\":1,\"token\":$token,\"type\":\"app_status\"}")
        delay(200)
        val status = if (_cameraStatus.value.isRecording) {
            com.asus.recosmart.domain.model.DeviceStatus.RECORD
        } else {
            com.asus.recosmart.domain.model.DeviceStatus.VF
        }
        val jsonRx = "{\"rval\":0,\"msg_id\":1,\"type\":\"app_status\",\"param\":\"${status.wireName}\"}"
        log("[MOCK RX] <- $jsonRx")
        return Result.success(status)
    }

    override suspend fun sendRawCommand(msgId: Int, param: String?): Result<CameraResponse> {
        if (msgId == 257) {
            val simulatedToken = if (_cameraStatus.value.activeToken > 0) _cameraStatus.value.activeToken else 1001
            log("[MOCK RAW TX] -> {\"msg_id\":257,\"token\":0}")
            delay(300)
            val jsonRx = "{\"rval\":0,\"msg_id\":257,\"param\":$simulatedToken}"
            log("[MOCK RAW RX] <- $jsonRx")

            _sessionState.value = SessionState.Connected(simulatedToken)
            _cameraStatus.value = _cameraStatus.value.copy(isConnected = true, activeToken = simulatedToken)
            val parsedResponse = ResponseParser.parse(jsonRx)
            return Result.success(parsedResponse)
        }

        if (msgId == 11) {
            return getDeviceInformation()
        }

        if (msgId == 259) {
            return prepareLiveView()
        }

        if (msgId == 260) {
            return stopLiveView()
        }

        if (msgId == 53270) {
            return takePhotoPiv()
        }

        if (msgId == 1 && param == "app_status") {
            val statusRes = getAppStatus()
            val status = statusRes.getOrDefault(com.asus.recosmart.domain.model.DeviceStatus.VF)
            val jsonRx = "{\"rval\":0,\"msg_id\":1,\"type\":\"app_status\",\"param\":\"${status.wireName}\"}"
            return Result.success(CameraResponse(msgId = 1, rval = 0, token = _cameraStatus.value.activeToken, rawResponse = jsonRx))
        }

        val token = _cameraStatus.value.activeToken
        val paramJson = if (param.isNullOrEmpty()) "" else ",\"param\":\"$param\""
        log("[MOCK RAW TX] -> {\"msg_id\":$msgId$paramJson,\"token\":$token}")
        delay(300)
        val response = CameraResponse(msgId = msgId, rval = 0, token = token, rawResponse = "{\"rval\":0,\"msg_id\":$msgId}")
        log("[MOCK RAW RX] <- ${response.rawResponse}")
        return Result.success(response)
    }

    override fun toggleMockMode(enabled: Boolean) {
        _isMockMode.value = enabled
        if (enabled) {
            _sessionState.value = SessionState.Disconnected
            _cameraStatus.value = _cameraStatus.value.copy(isConnected = false, activeToken = 0, isRecording = false)
        }
        log("[MOCK SYSTEM] Mock mode toggled: $enabled")
    }

    override fun clearDebugLogs() {
        _debugLogs.value = emptyList()
    }

    override fun logRtsp(message: String) {
        log(message)
    }

    override fun setFirstVideoFrameRendered(rendered: Boolean) {
        _cameraStatus.value = _cameraStatus.value.copy(firstVideoFrameRendered = rendered)
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$time] $msg"
        val current = _debugLogs.value.toMutableList()
        if (current.size > 200) current.removeAt(0)
        current.add(entry)
        _debugLogs.value = current
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}
