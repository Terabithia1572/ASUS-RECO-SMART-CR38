package com.asus.recosmart.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asus.recosmart.RecoSmartApp
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DebugConsoleViewModel(
    private val repository: CameraRepository = RecoSmartApp.instance.cameraRepository
) : ViewModel() {

    val debugLogs: StateFlow<List<String>> = repository.debugLogs
    val cameraStatus: StateFlow<CameraStatus> = repository.cameraStatus
    val isMockMode: StateFlow<Boolean> = repository.isMockMode

    private val _customMsgId = MutableStateFlow("257")
    val customMsgId: StateFlow<String> = _customMsgId.asStateFlow()

    private val _customParam = MutableStateFlow("")
    val customParam: StateFlow<String> = _customParam.asStateFlow()

    fun onMsgIdChanged(newMsgId: String) {
        _customMsgId.value = newMsgId
    }

    fun onParamChanged(newParam: String) {
        _customParam.value = newParam
    }

    fun sendCustomCommand() {
        viewModelScope.launch {
            val msgIdInt = _customMsgId.value.toIntOrNull() ?: 257
            repository.sendRawCommand(msgIdInt, _customParam.value.ifEmpty { null })
        }
    }

    fun selectQuickCommand(msgId: String, param: String) {
        _customMsgId.value = msgId
        _customParam.value = param
    }

    fun runSelfTest() {
        viewModelScope.launch {
            repository.logRtsp("==================================================")
            repository.logRtsp("[SELF-TEST] Starting Mock Automated Protocol Self-Test...")
            var passed = 0
            val totalTests = 11

            // Step 1: START_SESSION
            val sessionRes = repository.startSession()
            if (sessionRes.isSuccess && sessionRes.getOrNull() == 1001) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] START_SESSION -> Token 1001 acquired")
            } else {
                repository.logRtsp("[FAIL 1/$totalTests] START_SESSION failed: ${sessionRes.exceptionOrNull()}")
            }

            // Step 2: GET_ALL_CURRENT_SETTINGS
            val settingsRes = repository.fetchAllSettings()
            if (settingsRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] GET_ALL_CURRENT_SETTINGS -> ${settingsRes.getOrNull()?.size} settings parsed")
            } else {
                repository.logRtsp("[FAIL 2/$totalTests] GET_ALL_CURRENT_SETTINGS failed")
            }

            // Step 3: RESET_TO_VF
            val vfRes = repository.prepareLiveView()
            if (vfRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] RESET_TO_VF -> rval=0, state VF")
            } else {
                repository.logRtsp("[FAIL 3/$totalTests] RESET_TO_VF failed")
            }

            // Step 4: GET_APP_STATUS
            val statusRes = repository.getAppStatus()
            if (statusRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] GET_APP_STATUS -> wire state: ${statusRes.getOrNull()?.wireName}")
            } else {
                repository.logRtsp("[FAIL 4/$totalTests] GET_APP_STATUS failed")
            }

            // Step 5: TAKE_PHOTO
            val photoRes = repository.takePhoto()
            if (photoRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] TAKE_PHOTO (msg_id 769) -> Photo captured")
            } else {
                repository.logRtsp("[FAIL 5/$totalTests] TAKE_PHOTO failed")
            }

            // Step 6: RECORD_START
            val recStartRes = repository.startRecording()
            if (recStartRes.isSuccess && repository.cameraStatus.value.isRecording) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] RECORD_START (msg_id 513) -> State RECORD")
            } else {
                repository.logRtsp("[FAIL 6/$totalTests] RECORD_START failed")
            }

            // Step 7: PHOTO_PIV
            val pivRes = repository.takePhotoPiv()
            if (pivRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] PHOTO_PIV (msg_id 53270) -> Still capture during recording")
            } else {
                repository.logRtsp("[FAIL 7/$totalTests] PHOTO_PIV failed")
            }

            // Step 8: RECORD_STOP
            val recStopRes = repository.stopRecording()
            if (recStopRes.isSuccess && !repository.cameraStatus.value.isRecording) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] RECORD_STOP (msg_id 514) -> State VF")
            } else {
                repository.logRtsp("[FAIL 8/$totalTests] RECORD_STOP failed")
            }

            // Step 9: LS
            val filesRes = repository.listFiles()
            if (filesRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] LS (msg_id 1282) -> ${filesRes.getOrNull()?.size} items listed")
            } else {
                repository.logRtsp("[FAIL 9/$totalTests] LS failed")
            }

            // Step 10: STOP_VF
            val stopVfRes = repository.stopLiveView()
            if (stopVfRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] STOP_VF (msg_id 260) -> Stream teardown verified")
            } else {
                repository.logRtsp("[FAIL 10/$totalTests] STOP_VF failed")
            }

            // Step 11: GET_DEVICE_INFORMATION
            val devInfoRes = repository.getDeviceInformation()
            if (devInfoRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] GET_DEVICE_INFORMATION (msg_id 11) -> Device info verified")
            } else {
                repository.logRtsp("[FAIL 11/$totalTests] GET_DEVICE_INFORMATION failed")
            }

            repository.logRtsp("[SELF-TEST COMPLETED] Result: $passed / $totalTests PASSED")
            repository.logRtsp("==================================================")
        }
    }

    fun runHardwareDiagnostic() {
        viewModelScope.launch {
            repository.logRtsp("==================================================")
            repository.logRtsp("=== ASUS CR38 HARDWARE DIAGNOSTIC ===")
            repository.logRtsp("Target: 192.168.42.1:7878 | Camera Model: ASUS RECO Smart CR38")

            if (repository.isMockMode.value) {
                repository.logRtsp("[PROBE NOTICE] App is currently in MOCK MODE. Toggle to Real Camera mode in Connect tab to probe physical CR38 hardware.")
            }

            var netPass = false
            var tcpPass = false
            var sessionPass = false
            var devInfoPass = false
            var appStatusPass = false
            var settingsPass = false
            var lsPass = false
            var acquiredToken = 0

            // Stage 0: Network Pre-flight
            repository.logRtsp("[PRE-FLIGHT] Checking Wi-Fi and 192.168.42.1 reachability...")
            val preflight = com.asus.recosmart.data.network.CameraNetworkManager.performNetworkPreflight()
            netPass = preflight.isWifiConnected || preflight.isHostReachable
            tcpPass = preflight.isPortOpen

            repository.logRtsp("Network/Wi-Fi ........ ${if (netPass) "PASS" else "FAIL"}")
            repository.logRtsp("192.168.42.1:7878 .... ${if (tcpPass) "PASS (${preflight.latencyMs}ms)" else "FAIL"}")

            if (!tcpPass && !repository.isMockMode.value) {
                repository.logRtsp("[STAGE FAIL] TCP 192.168.42.1:7878 unreachable. Skipping subsequent authenticated protocol stages.")
                repository.logRtsp("START_SESSION ........ SKIPPED")
                repository.logRtsp("Token ................ NONE")
                repository.logRtsp("DEVICE_INFORMATION ... SKIPPED")
                repository.logRtsp("APP_STATUS ........... SKIPPED")
                repository.logRtsp("GET_SETTINGS ......... SKIPPED")
                repository.logRtsp("LS ................... SKIPPED")
                repository.logRtsp("RTSP ................. NOT TESTED")
                repository.logRtsp("Overall: CONNECTION FAILED")
                repository.logRtsp("==================================================")
                return@launch
            }

            // Stage 1: TCP Connect & START_SESSION
            repository.logRtsp("[STAGE 1] Opening TCP socket & sending START_SESSION (msg_id 257)...")
            val connRes = repository.connect("192.168.42.1", 7878)
            if (connRes.isSuccess) {
                sessionPass = true
                val state = repository.sessionState.value
                if (state is com.asus.recosmart.domain.model.SessionState.Connected) {
                    acquiredToken = state.token
                }
                repository.logRtsp("TCP Socket ........... PASS")
                repository.logRtsp("START_SESSION ........ PASS")
                repository.logRtsp("Token ................ $acquiredToken")
            } else {
                repository.logRtsp("TCP Socket ........... FAIL (${connRes.exceptionOrNull()?.localizedMessage})")
                repository.logRtsp("START_SESSION ........ FAIL")
                repository.logRtsp("Token ................ NONE")
                repository.logRtsp("DEVICE_INFORMATION ... SKIPPED")
                repository.logRtsp("APP_STATUS ........... SKIPPED")
                repository.logRtsp("GET_SETTINGS ......... SKIPPED")
                repository.logRtsp("LS ................... SKIPPED")
                repository.logRtsp("RTSP ................. NOT TESTED")
                repository.logRtsp("Overall: CONNECTION FAILED")
                repository.logRtsp("==================================================")
                return@launch
            }

            kotlinx.coroutines.delay(350)

            // Stage 2: GET_DEVICE_INFORMATION (msg_id 11)
            repository.logRtsp("[STAGE 2] Querying GET_DEVICE_INFORMATION (msg_id 11)...")
            val devInfoRes = repository.getDeviceInformation()
            devInfoPass = devInfoRes.isSuccess
            repository.logRtsp("DEVICE_INFORMATION ... ${if (devInfoPass) "PASS" else "FAIL"}")

            kotlinx.coroutines.delay(350)

            // Stage 3: GET_SETTING app_status (msg_id 1)
            repository.logRtsp("[STAGE 3] Querying GET_SETTING app_status (msg_id 1)...")
            val statusRes = repository.getAppStatus()
            appStatusPass = statusRes.isSuccess
            repository.logRtsp("APP_STATUS ........... ${if (appStatusPass) "PASS (${statusRes.getOrNull()?.wireName})" else "FAIL"}")

            kotlinx.coroutines.delay(350)

            // Stage 4: GET_ALL_CURRENT_SETTINGS (msg_id 3)
            repository.logRtsp("[STAGE 4] Querying GET_ALL_CURRENT_SETTINGS (msg_id 3)...")
            val settingsRes = repository.fetchAllSettings()
            settingsPass = settingsRes.isSuccess
            repository.logRtsp("GET_SETTINGS ......... ${if (settingsPass) "PASS" else "FAIL"}")

            kotlinx.coroutines.delay(350)

            // Stage 5: LS (msg_id 1282)
            repository.logRtsp("[STAGE 5] Querying LS /tmp/fuse_d/DCIM/ (msg_id 1282)...")
            val filesRes = repository.listFiles("/tmp/fuse_d/DCIM/")
            lsPass = filesRes.isSuccess
            repository.logRtsp("LS ................... ${if (lsPass) "PASS (${filesRes.getOrNull()?.size ?: 0} items)" else "FAIL"}")
            repository.logRtsp("RTSP ................. NOT TESTED")

            val overall = if (sessionPass && devInfoPass && appStatusPass && settingsPass && lsPass) "READY" else "PARTIAL"
            repository.logRtsp("Overall: $overall")
            repository.logRtsp("==================================================")
        }
    }

    fun clearLogs() {
        repository.clearDebugLogs()
    }

    fun copyLogsToClipboard(context: android.content.Context) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val modeStr = if (isMockMode.value) "MOCK_SIMULATOR" else "REAL_CAMERA"
        val session = repository.sessionState.value
        val token = cameraStatus.value.activeToken
        val ip = cameraStatus.value.cameraIp
        val port = cameraStatus.value.commandPort

        val header = """
            === ASUS RECO SMART CR38 PROTOCOL LOG EXPORT ===
            Export Timestamp: $timestamp
            App Version: 1.0.0 (Phase I Build)
            Android OS: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
            Device Model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
            Connection Mode: $modeStr
            Target Camera IP: $ip
            Command TCP Port: $port
            Active Session Token: $token
            Session State: $session
            ==================================================
        """.trimIndent()

        val fullExportText = "$header\n\n" + debugLogs.value.joinToString("\n")
        val clip = android.content.ClipData.newPlainText("ASUS CR38 Protocol Logs", fullExportText)
        clipboard.setPrimaryClip(clip)
        repository.logRtsp("[SYSTEM] Full protocol diagnostic report copied to Android clipboard (${debugLogs.value.size} entries)")
    }
}
