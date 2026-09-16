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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultCameraRepository : CameraRepository {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val recoveryMutex = Mutex()

    private val tcpClient = TcpSocketClient()
    private val sessionManager = SessionManager()
    private val mockRepository = MockCameraRepository()

    private val _isMockMode = MutableStateFlow(true)
    override val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _cameraStatus = MutableStateFlow(CameraStatus())
    override val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()

    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    override val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    init {
        scope.launch {
            sessionManager.sessionState.collect { realSession ->
                if (!_isMockMode.value) {
                    _sessionState.value = realSession
                }
            }
        }
        scope.launch {
            mockRepository.sessionState.collect { mockSession ->
                if (_isMockMode.value) {
                    _sessionState.value = mockSession
                }
            }
        }
        scope.launch {
            mockRepository.cameraStatus.collect { mockStatus ->
                if (_isMockMode.value) {
                    _cameraStatus.value = mockStatus
                }
            }
        }
        scope.launch {
            tcpClient.logs.collect { logs ->
                if (!_isMockMode.value) {
                    _debugLogs.value = logs
                }
            }
        }
        scope.launch {
            mockRepository.debugLogs.collect { logs ->
                if (_isMockMode.value) {
                    _debugLogs.value = logs
                }
            }
        }
    }

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

            // Automatically query device info to populate brand, model, firmware
            val devInfoRes = getDeviceInformation()
            if (devInfoRes.isSuccess) {
                val devInfo = devInfoRes.getOrNull()
                if (devInfo != null) {
                    _cameraStatus.value = _cameraStatus.value.copy(
                        brand = devInfo.brand ?: "SanJet",
                        model = devInfo.model ?: "DR38AS",
                        firmwareVersion = devInfo.fwVer ?: "2501",
                        apiVersion = devInfo.apiVer ?: "2.8.00"
                    )
                    tcpClient.log("[REAL DEVINFO] Brand: ${_cameraStatus.value.brand}, Model: ${_cameraStatus.value.model}, FW: ${_cameraStatus.value.firmwareVersion}")
                }
            }

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
        _cameraStatus.value = _cameraStatus.value.copy(isConnected = false, activeToken = 0, isRecording = false, firstVideoFrameRendered = false)
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

    override suspend fun recoverSession(): Result<Int> {
        if (_isMockMode.value) {
            return mockRepository.recoverSession()
        }
        return recoveryMutex.withLock {
            withContext(Dispatchers.IO) {
                tcpClient.log("[REAL RECOVERY] ==================================================")
                tcpClient.log("[REAL RECOVERY] Initiating centralized session recovery sequence...")

                tcpClient.disconnect()
                _cameraStatus.value = _cameraStatus.value.copy(isConnected = false, activeToken = 0)

                delay(800)

                val ip = _cameraStatus.value.cameraIp.ifEmpty { CameraStatus.DEFAULT_CAMERA_IP }
                val port = if (_cameraStatus.value.commandPort > 0) _cameraStatus.value.commandPort else CameraStatus.DEFAULT_COMMAND_PORT

                tcpClient.log("[REAL RECOVERY] Step 1/4: Reconnecting TCP socket to $ip:$port...")
                val connRes = tcpClient.connectCommandSocket(ip, port)
                if (connRes.isFailure) {
                    val err = "Recovery connection failed: ${connRes.exceptionOrNull()?.localizedMessage}"
                    tcpClient.log("[REAL RECOVERY FAIL] $err")
                    sessionManager.setError(err)
                    return@withContext Result.failure(Exception(err))
                }

                tcpClient.log("[REAL RECOVERY] Step 2/4: Requesting NEW token via START_SESSION (msg_id 257)...")
                sessionManager.setSessionStarting()
                val startRes = startSession()
                if (startRes.isFailure) {
                    val err = "Recovery START_SESSION failed: ${startRes.exceptionOrNull()?.localizedMessage}"
                    tcpClient.log("[REAL RECOVERY FAIL] $err")
                    tcpClient.disconnect()
                    sessionManager.setError(err)
                    return@withContext Result.failure(Exception(err))
                }

                val newToken = startRes.getOrThrow()
                tcpClient.log("[REAL RECOVERY] Step 3/4: Acquired NEW session token: $newToken")

                tcpClient.log("[REAL RECOVERY] Step 4/4: Re-binding data socket (8787)...")
                tcpClient.connectDataSocket(ip, CameraStatus.DEFAULT_DATA_PORT)

                getDeviceInformation()

                tcpClient.log("[REAL RECOVERY] SUCCESS: Session fully recovered with new token $newToken")
                tcpClient.log("[REAL RECOVERY] ==================================================")

                Result.success(newToken)
            }
        }
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
            // Asynchronously refresh camera files without blocking
            scope.launch {
                delay(1200)
                listFiles(CameraStatus.DEFAULT_DCIM_PATH)
            }
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
        return respResult.map { resp ->
            com.asus.recosmart.data.protocol.ResponseParser.parseSettings(resp.rawResponse)
        }
    }

    override suspend fun updateSetting(key: String, value: String): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.updateSetting(key, value)
        return sendCommandInternal(CameraCommand.SetSetting(key, value))
    }

    override suspend fun listFiles(path: String): Result<List<CameraFile>> {
        if (_isMockMode.value) return mockRepository.listFiles(path)

        val rootPath = "/tmp/fuse_d/DCIM"
        tcpClient.log("[REAL FS] STEP 1: CD to root '$rootPath' (CD msg_id 1283)...")
        val cdRootRes = sendCommandInternal(CameraCommand.ChangeDir(rootPath))
        if (cdRootRes.isFailure || cdRootRes.getOrNull()?.isSuccess != true) {
            val err = cdRootRes.exceptionOrNull()?.localizedMessage ?: "CD to root DCIM failed"
            tcpClient.log("[REAL ERROR] $err")
            return Result.failure(Exception(err))
        }

        tcpClient.log("[REAL FS] STEP 2: LS root directory...")
        val lsRootRes = sendCommandInternal(CameraCommand.ListFiles(""))
        if (lsRootRes.isFailure || lsRootRes.getOrNull()?.isSuccess != true) {
            val err = lsRootRes.exceptionOrNull()?.localizedMessage ?: "LS root DCIM failed"
            tcpClient.log("[REAL ERROR] $err")
            return Result.failure(Exception(err))
        }

        val rawRootResponse = lsRootRes.getOrThrow().rawResponse
        val rootDirs = com.asus.recosmart.data.protocol.ResponseParser.parseDirectories(rawRootResponse)

        if (rootDirs.isEmpty()) {
            tcpClient.log("[REAL FS] No subdirectories in DCIM root. Parsing files directly from root...")
            val rootFiles = com.asus.recosmart.data.protocol.ResponseParser.parseFilesListing(rawRootResponse, "DCIM")
            return Result.success(rootFiles)
        }

        tcpClient.log("[REAL FS] Discovered ${rootDirs.size} MEDIA directories: ${rootDirs.joinToString { it.name }}")

        val allMediaFiles = mutableListOf<CameraFile>()

        for (dir in rootDirs) {
            val dirPath = dir.remotePath
            tcpClient.log("[REAL FS] Enumerating MEDIA folder '${dir.name}' ($dirPath)...")
            val cdDirRes = sendCommandInternal(CameraCommand.ChangeDir(dirPath))
            if (cdDirRes.isFailure || cdDirRes.getOrNull()?.isSuccess != true) {
                tcpClient.log("[REAL WARNING] Failed to CD to '${dir.name}', skipping.")
                continue
            }

            val lsDirRes = sendCommandInternal(CameraCommand.ListFiles(""))
            if (lsDirRes.isFailure || lsDirRes.getOrNull()?.isSuccess != true) {
                tcpClient.log("[REAL WARNING] Failed to LS '${dir.name}', skipping.")
                continue
            }

            val rawDirResponse = lsDirRes.getOrThrow().rawResponse
            val folderFiles = com.asus.recosmart.data.protocol.ResponseParser.parseFilesListing(rawDirResponse, dir.name)
            tcpClient.log("[REAL FS] Folder '${dir.name}': parsed ${folderFiles.size} media files (paired with thumbnails).")
            allMediaFiles.addAll(folderFiles)
        }

        // Restore CD state back to root DCIM
        sendCommandInternal(CameraCommand.ChangeDir(rootPath))

        tcpClient.log("[REAL FS] TOTAL ENUMERATED MEDIA: ${allMediaFiles.size} files across ${rootDirs.size} folders.")
        return Result.success(allMediaFiles)
    }

    override suspend fun deleteFile(filePath: String): Result<CameraResponse> {
        if (_isMockMode.value) return mockRepository.deleteFile(filePath)
        tcpClient.log("[REAL FS] Deleting file '$filePath'...")
        val mainRes = sendCommandInternal(CameraCommand.DeleteFile(filePath))
        
        if (mainRes.getOrNull()?.isSuccess == true) {
            if (!filePath.contains("_thm")) {
                val extIndex = filePath.lastIndexOf('.')
                if (extIndex > 0) {
                    val thmPath = filePath.substring(0, extIndex) + "_thm" + filePath.substring(extIndex)
                    tcpClient.log("[REAL FS] Deleting companion thumbnail file '$thmPath'...")
                    sendCommandInternal(CameraCommand.DeleteFile(thmPath))
                }
            }
        }
        return mainRes
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

        tcpClient.log("==================================================")
        tcpClient.log("[LIVE INIT] Starting legacy viewfinder initialization sequence...")

        // Stage 1: Load current settings
        tcpClient.log("[LIVE INIT 1/6] GET_ALL_CURRENT_SETTINGS (msg_id 3)...")
        val settingsRes = sendCommandInternal(CameraCommand.GetAllCurrentSettings)
        if (settingsRes.isFailure) {
            tcpClient.log("[LIVE INIT 1/6 NOTICE] GET_ALL_CURRENT_SETTINGS notice: ${settingsRes.exceptionOrNull()?.localizedMessage}")
        } else {
            tcpClient.log("[LIVE INIT 1/6 PASS] GET_ALL_CURRENT_SETTINGS OK")
        }

        // Stage 2: Stop existing VF stream if any (STOP_VF msg_id 260)
        tcpClient.log("[LIVE INIT 2/6] STOP_VF (msg_id 260)...")
        val stopVfRes = sendCommandInternal(CameraCommand.StopVf)
        if (stopVfRes.isFailure) {
            tcpClient.log("[LIVE INIT 2/6 NOTICE] STOP_VF notice: ${stopVfRes.exceptionOrNull()?.localizedMessage}")
        } else {
            tcpClient.log("[LIVE INIT 2/6 PASS] STOP_VF OK")
        }

        // Stage 3: Set stream_out_type to rtsp
        tcpClient.log("[LIVE INIT 3/6] SET stream_out_type=rtsp (msg_id 2)...")
        val setStreamRes = sendCommandInternal(CameraCommand.SetSetting("stream_out_type", "rtsp"))
        if (setStreamRes.isFailure) {
            tcpClient.log("[LIVE INIT 3/6 NOTICE] SET stream_out_type notice: ${setStreamRes.exceptionOrNull()?.localizedMessage}")
        } else {
            tcpClient.log("[LIVE INIT 3/6 PASS] SET stream_out_type=rtsp OK")
        }

        // Stage 4: Set save_low_resolution_clip to on
        tcpClient.log("[LIVE INIT 4/6] SET save_low_resolution_clip=on (msg_id 2)...")
        val setLowResRes = sendCommandInternal(CameraCommand.SetSetting("save_low_resolution_clip", "on"))
        if (setLowResRes.isFailure) {
            tcpClient.log("[LIVE INIT 4/6 NOTICE] SET save_low_resolution_clip notice: ${setLowResRes.exceptionOrNull()?.localizedMessage}")
        } else {
            tcpClient.log("[LIVE INIT 4/6 PASS] SET save_low_resolution_clip=on OK")
        }

        delay(1000)

        // Stage 5: Reset VF (RESET_TO_VF msg_id 259 param force)
        tcpClient.log("[LIVE INIT 5/6] RESET_TO_VF force (msg_id 259)...")
        val resetVfRes = sendCommandInternal(CameraCommand.ResetToVf)
        if (resetVfRes.isFailure || resetVfRes.getOrNull()?.isSuccess != true) {
            val err = resetVfRes.exceptionOrNull()?.localizedMessage ?: "RESET_TO_VF rejected (rval=${resetVfRes.getOrNull()?.rval})"
            tcpClient.log("[LIVE INIT 5/6 FAIL] RESET_TO_VF failed: $err")
            return Result.failure(Exception(err))
        }
        tcpClient.log("[LIVE INIT 5/6 PASS] RESET_TO_VF OK")

        delay(500)

        // Stage 6: RTSP Probe Ready
        tcpClient.log("[LIVE INIT 6/6 PASS] RTSP URL ready: ${CameraStatus.DEFAULT_RTSP_URL}")
        tcpClient.log("==================================================")

        return resetVfRes
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
        CoroutineScope(Dispatchers.IO).launch {
            tcpClient.disconnect()
        }
        val targetStatus = if (enabled) mockRepository.cameraStatus.value else CameraStatus(isConnected = false, activeToken = 0, isRecording = false)
        val targetSession = if (enabled) mockRepository.sessionState.value else SessionState.Disconnected
        val targetLogs = if (enabled) mockRepository.debugLogs.value else tcpClient.logs.value
        _cameraStatus.value = targetStatus
        _sessionState.value = targetSession
        _debugLogs.value = targetLogs
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

    override fun setFirstVideoFrameRendered(rendered: Boolean) {
        if (_isMockMode.value) {
            mockRepository.setFirstVideoFrameRendered(rendered)
        }
        _cameraStatus.value = _cameraStatus.value.copy(firstVideoFrameRendered = rendered)
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
