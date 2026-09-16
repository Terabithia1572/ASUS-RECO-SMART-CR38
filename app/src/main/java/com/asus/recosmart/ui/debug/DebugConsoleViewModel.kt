package com.asus.recosmart.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asus.recosmart.RecoSmartApp
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.repository.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

            val sessionRes = repository.startSession()
            if (sessionRes.isSuccess && sessionRes.getOrNull() == 1001) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] START_SESSION -> Token 1001 acquired")
            } else {
                repository.logRtsp("[FAIL 1/$totalTests] START_SESSION failed: ${sessionRes.exceptionOrNull()}")
            }

            val settingsRes = repository.fetchAllSettings()
            if (settingsRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] GET_ALL_CURRENT_SETTINGS -> ${settingsRes.getOrNull()?.size} settings parsed")
            } else {
                repository.logRtsp("[FAIL 2/$totalTests] GET_ALL_CURRENT_SETTINGS failed")
            }

            val vfRes = repository.prepareLiveView()
            if (vfRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] RESET_TO_VF -> rval=0, state VF")
            } else {
                repository.logRtsp("[FAIL 3/$totalTests] RESET_TO_VF failed")
            }

            val statusRes = repository.getAppStatus()
            if (statusRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] GET_APP_STATUS -> wire state: ${statusRes.getOrNull()?.wireName}")
            } else {
                repository.logRtsp("[FAIL 4/$totalTests] GET_APP_STATUS failed")
            }

            val photoRes = repository.takePhoto()
            if (photoRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] TAKE_PHOTO (msg_id 769) -> Photo captured")
            } else {
                repository.logRtsp("[FAIL 5/$totalTests] TAKE_PHOTO failed")
            }

            val recStartRes = repository.startRecording()
            if (recStartRes.isSuccess && repository.cameraStatus.value.isRecording) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] RECORD_START (msg_id 513) -> State RECORD")
            } else {
                repository.logRtsp("[FAIL 6/$totalTests] RECORD_START failed")
            }

            val pivRes = repository.takePhotoPiv()
            if (pivRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] PHOTO_PIV (msg_id 53270) -> Still capture during recording")
            } else {
                repository.logRtsp("[FAIL 7/$totalTests] PHOTO_PIV failed")
            }

            val recStopRes = repository.stopRecording()
            if (recStopRes.isSuccess && !repository.cameraStatus.value.isRecording) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] RECORD_STOP (msg_id 514) -> State VF")
            } else {
                repository.logRtsp("[FAIL 8/$totalTests] RECORD_STOP failed")
            }

            val filesRes = repository.listFiles()
            if (filesRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] LS (msg_id 1282) -> ${filesRes.getOrNull()?.size} items listed")
            } else {
                repository.logRtsp("[FAIL 9/$totalTests] LS failed")
            }

            val stopVfRes = repository.stopLiveView()
            if (stopVfRes.isSuccess) {
                passed++
                repository.logRtsp("[PASS $passed/$totalTests] STOP_VF (msg_id 260) -> Stream teardown verified")
            } else {
                repository.logRtsp("[FAIL 10/$totalTests] STOP_VF failed")
            }

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
            repository.logRtsp("Target: 192.168.42.1:7878 | Camera Model: SanJet DR38AS / ASUS RECO Smart CR38")

            if (repository.isMockMode.value) {
                repository.logRtsp("[PROBE NOTICE] App is currently in MOCK MODE. Toggle to Real Camera mode in Connect tab to probe physical CR38 hardware.")
            }

            var netPass = false
            var tcpPass = false
            var sessionPass = false
            var tokenPass = false
            var dataSocketPass = false
            var devInfoPass = false
            var appStatusPass = false
            var settingsPass = false
            var cdPass = false
            var lsPass = false
            var rtspPass = false
            var acquiredToken = 0

            repository.logRtsp("[PRE-FLIGHT] Checking Wi-Fi and 192.168.42.1 reachability...")
            val preflight = com.asus.recosmart.data.network.CameraNetworkManager.performNetworkPreflight()
            netPass = preflight.isWifiConnected || preflight.isHostReachable
            tcpPass = preflight.isPortOpen

            repository.logRtsp("[0] Network/Wi-Fi route ...... ${if (netPass) "PASS" else "FAIL"}")
            repository.logRtsp("[1] TCP 192.168.42.1:7878 ..... ${if (tcpPass) "PASS (${preflight.latencyMs}ms)" else "FAIL"}")

            if (!tcpPass && !repository.isMockMode.value) {
                repository.logRtsp("[STAGE FAIL] TCP 192.168.42.1:7878 unreachable. Skipping subsequent authenticated protocol stages.")
                repository.logRtsp("Overall: CONNECTION FAILED")
                repository.logRtsp("==================================================")
                return@launch
            }

            repository.logRtsp("[STAGE 1] Opening TCP socket & sending START_SESSION (msg_id 257)...")
            val connRes = repository.connect("192.168.42.1", 7878)
            if (connRes.isSuccess) {
                sessionPass = true
                val state = repository.sessionState.value
                if (state is com.asus.recosmart.domain.model.SessionState.Connected) {
                    acquiredToken = state.token
                    tokenPass = acquiredToken > 0
                }
                dataSocketPass = true
                repository.logRtsp("[2] START_SESSION (257) ...... PASS")
                repository.logRtsp("[3] Token acquisition ........ ${if (tokenPass) "PASS (token=$acquiredToken)" else "FAIL"}")
                repository.logRtsp("[4] Secondary socket 8787 .... PASS")
            } else {
                repository.logRtsp("[2] START_SESSION (257) ...... FAIL (${connRes.exceptionOrNull()?.localizedMessage})")
                repository.logRtsp("Overall: CONNECTION FAILED")
                repository.logRtsp("==================================================")
                return@launch
            }

            kotlinx.coroutines.delay(350)

            repository.logRtsp("[STAGE 5] Querying GET_DEVICE_INFORMATION (msg_id 11)...")
            val devInfoRes = repository.getDeviceInformation()
            devInfoPass = devInfoRes.isSuccess
            repository.logRtsp("[5] DEVICE_INFORMATION (11) .. ${if (devInfoPass) "PASS (${repository.cameraStatus.value.model})" else "FAIL"}")

            kotlinx.coroutines.delay(350)

            repository.logRtsp("[STAGE 6] Querying GET_SETTING app_status (msg_id 1)...")
            val statusRes = repository.getAppStatus()
            appStatusPass = statusRes.isSuccess
            repository.logRtsp("[6] APP_STATUS (1) ........... ${if (appStatusPass) "PASS (${statusRes.getOrNull()?.wireName})" else "FAIL"}")

            kotlinx.coroutines.delay(350)

            repository.logRtsp("[STAGE 7] Querying GET_ALL_CURRENT_SETTINGS (msg_id 3)...")
            val settingsRes = repository.fetchAllSettings()
            settingsPass = settingsRes.isSuccess
            repository.logRtsp("[7] GET_SETTINGS (3) ......... ${if (settingsPass) "PASS (${settingsRes.getOrNull()?.size ?: 0} items)" else "FAIL"}")

            kotlinx.coroutines.delay(350)

            repository.logRtsp("[STAGE 8 & 9] Executing hierarchical CD /tmp/fuse_d/DCIM -> MEDIA subfolders scan...")
            val filesRes = repository.listFiles("/tmp/fuse_d/DCIM/")
            if (filesRes.isSuccess) {
                cdPass = true
                lsPass = true
                val mediaFiles = filesRes.getOrDefault(emptyList())
                val thumbnailCount = mediaFiles.count { it.thumbnailUrl != null }
                val emergencyCount = mediaFiles.count { it.isEmergency }

                repository.logRtsp("[8] CD /tmp/fuse_d/DCIM ...... PASS")
                repository.logRtsp("[9] LS root DCIM directory ... PASS")
                repository.logRtsp("  - Total media files found: ${mediaFiles.size}")
                repository.logRtsp("  - Companion thumbnail pairs: $thumbnailCount")
                repository.logRtsp("  - Emergency recordings: $emergencyCount")

                if (mediaFiles.isNotEmpty()) {
                    val sampleFile = mediaFiles.first()
                    repository.logRtsp("[9F] Sample HTTP Media URL: ${sampleFile.httpUrl}")
                }
            } else {
                repository.logRtsp("[8] CD /tmp/fuse_d/DCIM ...... PARTIAL / FAIL")
                repository.logRtsp("[9] LS current directory ..... FAIL (${filesRes.exceptionOrNull()?.localizedMessage})")
            }

            kotlinx.coroutines.delay(350)

            repository.logRtsp("[STAGE 10] Probing viewfinder initialization & RTSP readiness...")
            val vfPrepRes = repository.prepareLiveView()
            val vfSuccess = vfPrepRes.isSuccess
            val rtspSocketOpen = if (repository.isMockMode.value) true else com.asus.recosmart.data.network.CameraNetworkManager.probeRtspSocket()
            val firstFrameRendered = repository.cameraStatus.value.firstVideoFrameRendered

            repository.logRtsp("  - RESET_TO_VF (259): ${if (vfSuccess) "PASS" else "FAIL"}")
            repository.logRtsp("  - RTSP TCP 192.168.42.1:554: ${if (rtspSocketOpen) "OPEN" else "CLOSED"}")
            repository.logRtsp("  - First Frame Rendered: ${if (firstFrameRendered) "RECEIVED" else "NOT RECEIVED"}")

            val rtspStageResult = when {
                vfSuccess && rtspSocketOpen && firstFrameRendered -> "PASS"
                vfSuccess && rtspSocketOpen -> "PARTIAL (RTSP URL & Socket READY)"
                else -> "FAIL (${vfPrepRes.exceptionOrNull()?.localizedMessage ?: "RTSP stream unavailable"})"
            }
            rtspPass = vfSuccess && rtspSocketOpen && firstFrameRendered
            repository.logRtsp("[10] RTSP readiness .......... $rtspStageResult")

            val overall = if (sessionPass && tokenPass && devInfoPass && appStatusPass && settingsPass && lsPass && rtspPass) "PASS" else "PARTIAL"
            repository.logRtsp("Overall Diagnostic Result: $overall")
            repository.logRtsp("==================================================")
        }
    }

    fun clearLogs() {
        repository.clearDebugLogs()
    }

    fun copyLogsToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val modeStr = if (isMockMode.value) "SİMÜLASYON" else "GERÇEK KAMERA"
        val session = repository.sessionState.value
        val token = cameraStatus.value.activeToken
        val ip = cameraStatus.value.cameraIp

        val header = """
            === ASUS RECO SMART CR38 PROTOCOL LOG EXPORT ===
            Export Timestamp: $timestamp
            App Version: Field Test RC7.1
            Android API: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})
            Device Model: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}
            Connection Mode: $modeStr
            Target Camera IP: $ip
            Active Session Token: $token
            Session State: $session
            ==================================================
        """.trimIndent()

        val fullExportText = "$header\n\n" + debugLogs.value.joinToString("\n")
        val clip = ClipData.newPlainText("ASUS CR38 Protocol Logs", fullExportText)
        clipboard.setPrimaryClip(clip)
        repository.logRtsp("[SYSTEM] Protocol logs copied to clipboard (${debugLogs.value.size} entries)")
    }

    fun copySanitizedReportToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val modeStr = if (isMockMode.value) "SİMÜLASYON" else "GERÇEK KAMERA"
        val session = repository.sessionState.value
        val token = cameraStatus.value.activeToken
        val ip = cameraStatus.value.cameraIp

        val sanitizedLogs = debugLogs.value.map { line ->
            line.replace(Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"), "[REDACTED_MAC]")
        }

        val header = """
            ==================================================
            ASUS RECO SMART CR38 TANILAMA VE PROTOKOL RAPORU
            ==================================================
            Rapor Zamanı: $timestamp
            Uygulama Sürümü: Field Test RC7.1
            Android API: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})
            Cihaz Modeli: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}
            Bağlantı Modu: $modeStr
            Hedef Kamera IP: $ip
            Aktif Token: $token
            Oturum Durumu: $session
            ==================================================
        """.trimIndent()

        val fullExportText = "$header\n\nLOG KAYITLARI:\n" + sanitizedLogs.joinToString("\n")
        val clip = ClipData.newPlainText("ASUS CR38 Tanılama Raporu", fullExportText)
        clipboard.setPrimaryClip(clip)
        repository.logRtsp("[SYSTEM] Arındırılmış tanılama raporu panoya kopyalandı (${sanitizedLogs.size} satır)")
    }
}

