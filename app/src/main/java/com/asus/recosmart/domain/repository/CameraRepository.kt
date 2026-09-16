package com.asus.recosmart.domain.repository

import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.domain.model.CameraResponse
import com.asus.recosmart.domain.model.CameraSetting
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.model.SessionState
import kotlinx.coroutines.flow.StateFlow

interface CameraRepository {
    val sessionState: StateFlow<SessionState>
    val cameraStatus: StateFlow<CameraStatus>
    val debugLogs: StateFlow<List<String>>
    val isMockMode: StateFlow<Boolean>

    suspend fun connect(ip: String = CameraStatus.DEFAULT_CAMERA_IP, port: Int = CameraStatus.DEFAULT_COMMAND_PORT): Result<Unit>
    suspend fun disconnect()
    suspend fun startSession(): Result<Int>
    suspend fun startRecording(): Result<CameraResponse>
    suspend fun stopRecording(): Result<CameraResponse>
    suspend fun takePhoto(): Result<CameraResponse>
    suspend fun fetchAllSettings(): Result<List<CameraSetting>>
    suspend fun updateSetting(key: String, value: String): Result<CameraResponse>
    suspend fun listFiles(path: String = CameraStatus.DEFAULT_DCIM_PATH): Result<List<CameraFile>>
    suspend fun deleteFile(filePath: String): Result<CameraResponse>
    suspend fun formatSdCard(): Result<CameraResponse>
    suspend fun factoryReset(): Result<CameraResponse>
    suspend fun resetToVf(): Result<CameraResponse>
    suspend fun prepareLiveView(): Result<CameraResponse>
    suspend fun stopLiveView(): Result<CameraResponse>
    suspend fun takePhotoPiv(): Result<CameraResponse>
    suspend fun getDeviceInformation(): Result<CameraResponse>
    suspend fun getAppStatus(): Result<com.asus.recosmart.domain.model.DeviceStatus>
    suspend fun sendRawCommand(msgId: Int, param: String? = null): Result<CameraResponse>
    fun toggleMockMode(enabled: Boolean)
    fun clearDebugLogs()
    fun logRtsp(message: String)
    fun setFirstVideoFrameRendered(rendered: Boolean)
}
