package com.asus.recosmart.data.repository

import com.asus.recosmart.data.mock.MockCameraRepository
import com.asus.recosmart.data.network.SessionManager
import com.asus.recosmart.data.network.TcpSocketClient
import com.asus.recosmart.domain.model.CameraCommand
import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.domain.model.CameraResponse
import com.asus.recosmart.domain.model.CameraSetting
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.model.SessionState
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DefaultCameraRepository : CameraRepository {

    private val tcpClient = TcpSocketClient()
    private val sessionManager = SessionManager()
    private val mockRepository = MockCameraRepository()

    private val _isMockMode = MutableStateFlow(true)
    override val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private val _cameraStatus = MutableStateFlow(CameraStatus())
    override val cameraStatus: StateFlow<CameraStatus>
        get() = if (_isMockMode.value) mockRepository.cameraStatus else _cameraStatus.asStateFlow()

    override val sessionState: StateFlow<SessionState>
        get() = if (_isMockMode.value) mockRepository.sessionState else sessionManager.sessionState

    override val debugLogs: StateFlow<List<String>>
        get() = if (_isMockMode.value) mockRepository.debugLogs else tcpClient.logs

    override suspend fun connect(ip: String, port: Int): Result<Unit> {
        if (_isMockMode.value) {
            return mockRepository.connect(ip, port)
        }

        sessionManager.setTcpConnected()
        tcpClient.log("[REAL CONNECT] Stage 1/3: Connecting TCP command socket to $ip:$port...")

        // Step 1: Connect Command TCP socket (Port 7878)
        val cmdConnRes = tcpClient.connectCommandSocket(ip, port)
        if (cmdConnRes.isFailure) {
            val err = cmdConnRes.exceptionOrNull()?.localizedMessage ?: "Failed to connect command socket"
            sessionManager.setError("TCP Connection failed: $err")
            return Result.failure(cmdConnRes.exceptionOrNull() ?: Exception(err))
        }

        // Step 2: Stage 2/3 - Perform START_SESSION handshake
        sessionManager.setSessionStarting()
        tcpClient.log("[REAL SESSION] Stage 2/3: Sending START_SESSION (msg_id 257)...")

        val sessionRes = startSession()
        return if (sessionRes.isSuccess) {
            val acquiredToken = sessionRes.getOrThrow()
            tcpClient.log("[REAL SESSION] Stage 3/3: Session ACTIVE with acquired token: $acquiredToken")

            // Step 3: Connect Data TCP socket (Port 8787)
            val dataPort = CameraStatus.DEFAULT_DATA_PORT
            tcpClient.connectDataSocket(ip, dataPort)

            _cameraStatus.value = _cameraStatus.value.copy(
                isConnected = true,
                cameraIp = ip,
                commandPort = port,
                activeToken = acquiredToken
            )
            Result.success(Unit)
        } else {
            val err = sessionRes.exceptionOrNull()?.localizedMessage ?: "START_SESSION failed"
            tcpClient.disconnect()
            sessionManager.setError(err)
            Result.failure(sessionRes.exceptionOrNull() ?: Exception(err))
        }
    }

    override suspend fun disconnect() {
        if (_isMockMode.value) {
            mockRepository.disconnect()
            return
        }
        tcpClient.disconnect()
        sessionManager.setDisconnected()
        _cameraStatus.value = _cameraStatus.value.copy(isConnected = false, activeToken = 0, isRecording = false)
    }

    override suspend fun startSession(): Result<Int> {
        if (_isMockMode.value) {
            return mockRepository.startSession()
        }
        val responseResult = tcpClient.sendCommand(CameraCommand.StartSession, token = 0)
        return responseResult.fold(
            onSuccess = { resp ->
                if (resp.isSuccess) {
                    val token = resp.token
                    sessionManager.setConnected(token)
                    _cameraStatus.value = _cameraStatus.value.copy(activeToken = token)
                    Result.success(token)
                } else {
                    val errMsg = "Start session rejected: rval=${resp.rval}"
                    tcpClient.log("[REAL ERROR] $errMsg")
                    sessionManager.setError(errMsg)
                    Result.failure(Exception(errMsg))
                }
            },
            onFailure = { err ->
                val errMsg = "Start session network error: ${err.localizedMessage}"
                tcpClient.log("[REAL ERROR] $errMsg")
                sessionManager.setError(errMsg)
                Result.failure(err)
            }
        )
    }

    override suspend fun startRecording(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.startRecording()
        val result = sendCommandInternal(CameraCommand.RecordStart)
        if (result.getOrNull()?.isSuccess == true) {
            _cameraStatus.value = _cameraStatus.value.copy(isRecording = true)
        }
        return result
    }

    override suspend fun stopRecording(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.stopRecording()
        val result = sendCommandInternal(CameraCommand.RecordStop)
        if (result.getOrNull()?.isSuccess == true) {
            _cameraStatus.value = _cameraStatus.value.copy(isRecording = false)
        }
        return result
    }

    override suspend fun takePhoto(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.takePhoto()
        return sendCommandInternal(CameraCommand.TakePhoto)
    }

    override suspend fun fetchAllSettings(): Result<List<CameraSetting>> {
        if (_isMockMode.value) return mockRepository.fetchAllSettings()
        val respResult = sendCommandInternal(CameraCommand.GetAllCurrentSettings)
        return respResult.map { emptyList() }
    }

    override suspend fun updateSetting(key: String, value: String): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.updateSetting(key, value)
        return sendCommandInternal(CameraCommand.SetSetting(key, value))
    }

    override suspend fun listFiles(path: String): Result<List<CameraFile>> {
        if (_isMockMode.value) return mockRepository.listFiles(path)
        val respResult = sendCommandInternal(CameraCommand.ListFiles(path))
        return respResult.map { resp -> com.asus.recosmart.data.protocol.ResponseParser.parseListing(resp.rawResponse) }
    }

    override suspend fun deleteFile(filePath: String): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.deleteFile(filePath)
        return sendCommandInternal(CameraCommand.DeleteFile(filePath))
    }

    override suspend fun formatSdCard(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.formatSdCard()
        return sendCommandInternal(CameraCommand.FormatSdCard)
    }

    override suspend fun factoryReset(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.factoryReset()
        return sendCommandInternal(CameraCommand.SetSetting("factory default", "on"))
    }

    override suspend fun resetToVf(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.resetToVf()
        return sendCommandInternal(CameraCommand.ResetToVf)
    }

    override suspend fun prepareLiveView(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.prepareLiveView()
        return sendCommandInternal(CameraCommand.ResetToVf)
    }

    override suspend fun stopLiveView(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.stopLiveView()
        return sendCommandInternal(CameraCommand.StopVf)
    }

    override suspend fun takePhotoPiv(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.takePhotoPiv()
        return sendCommandInternal(CameraCommand.PhotoPiv)
    }

    override suspend fun getDeviceInformation(): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.getDeviceInformation()
        return sendCommandInternal(CameraCommand.GetDeviceInfo)
    }

    override suspend fun getAppStatus(): Result<com.asus.recosmart.domain.model.DeviceStatus> {
        if (_isMockMode.value) return mockRepository.getAppStatus()
        val res = sendCommandInternal(CameraCommand.GetAppStatus)
        return res.map { com.asus.recosmart.domain.model.DeviceStatus.fromWire(it.param ?: it.type) }
    }

    override suspend fun sendRawCommand(msgId: Int, param: String?): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.sendRawCommand(msgId, param)
        return sendCommandInternal(CameraCommand.CustomCommand(msgId, "RAW_$msgId", param))
    }

    override fun toggleMockMode(enabled: Boolean) {
        _isMockMode.value = enabled
        mockRepository.toggleMockMode(enabled)
        sessionManager.setDisconnected()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            tcpClient.disconnect()
        }
        _cameraStatus.value = _cameraStatus.value.copy(isConnected = false, activeToken = 0, isRecording = false)
    }

    override fun clearDebugLogs() {
        if (_isMockMode.value) {
            mockRepository.clearDebugLogs()
        } else {
            tcpClient.clearLogs()
        }
    }

    override fun logRtsp(message: String) {
        if (_isMockMode.value) {
            mockRepository.logRtsp(message)
        } else {
            tcpClient.log(message)
        }
    }

    private suspend fun sendCommandInternal(command: CameraCommand): Result<CameraResponse> {
        val currentSession = sessionManager.sessionState.value
        if (currentSession !is SessionState.Connected) {
            val err = "Cannot send command ${command.commandName}: Camera is not connected"
            tcpClient.log("[REAL ERROR] $err")
            return Result.failure(IllegalStateException(err))
        }
        val token = currentSession.token
        return tcpClient.sendCommand(command, token)
    }
}
