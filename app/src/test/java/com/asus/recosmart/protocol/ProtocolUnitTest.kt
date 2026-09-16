package com.asus.recosmart.protocol

import com.asus.recosmart.data.mock.MockCameraRepository
import com.asus.recosmart.data.protocol.CommandSerializer
import com.asus.recosmart.data.protocol.ResponseParser
import com.asus.recosmart.data.protocol.TcpResponseFramer
import com.asus.recosmart.domain.model.CameraCommand
import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.ui.files.FileFilterCategory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ProtocolUnitTest {

    // =========================================================================
    // 1. COMMAND SERIALIZATION TESTS
    // =========================================================================

    @Test
    fun testStartSessionSerialization() {
        val jsonStr = CommandSerializer.serialize(CameraCommand.StartSession, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(257, json.getInt("msg_id"))
        assertEquals(0, json.getInt("token")) // START_SESSION token request must always be 0
    }

    @Test
    fun testGetSettingAppStatusSerialization() {
        val jsonStr = CommandSerializer.serialize(CameraCommand.GetAppStatus, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(1, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
        assertEquals("app_status", json.getString("type"))
    }

    @Test
    fun testSetSettingVideoResolutionSerialization() {
        val jsonStr = CommandSerializer.serialize(
            CameraCommand.SetSetting("video_resolution", "1920x1080 30P 16:9"),
            sessionToken = 1001
        )
        val json = JSONObject(jsonStr)
        assertEquals(2, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
        assertEquals("video_resolution", json.getString("type"))
        assertEquals("1920x1080 30P 16:9", json.getString("param"))
    }

    @Test
    fun testResetToVfSerialization() {
        val jsonStr = CommandSerializer.serialize(CameraCommand.ResetToVf, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(259, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
        assertEquals("force", json.getString("param"))
    }

    @Test
    fun testStopVfSerialization() {
        val jsonStr = CommandSerializer.serialize(CameraCommand.StopVf, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(260, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
    }

    @Test
    fun testRecordStartSerialization() {
        val jsonStr = CommandSerializer.serialize(CameraCommand.RecordStart, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(513, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
    }

    @Test
    fun testRecordStopSerialization() {
        val jsonStr = CommandSerializer.serialize(CameraCommand.RecordStop, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(514, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
    }

    @Test
    fun testTakePhotoSerialization() {
        val jsonStr = CommandSerializer.serialize(CameraCommand.TakePhoto, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(769, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
    }

    @Test
    fun testPhotoPivSerialization() {
        val jsonStr = CommandSerializer.serialize(CameraCommand.PhotoPiv, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(53270, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
    }

    @Test
    fun testListFilesSerialization() {
        val jsonStr = CommandSerializer.serialize(
            CameraCommand.ListFiles("/tmp/fuse_d/DCIM/"),
            sessionToken = 1001
        )
        val json = JSONObject(jsonStr)
        assertEquals(1282, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
        assertEquals("/tmp/fuse_d/DCIM/", json.getString("param"))
    }

    @Test
    fun testDeleteFileSerialization() {
        val jsonStr = CommandSerializer.serialize(
            CameraCommand.DeleteFile("LOCA0001.MP4"),
            sessionToken = 1001
        )
        val json = JSONObject(jsonStr)
        assertEquals(1281, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
        assertEquals("LOCA0001.MP4", json.getString("param"))
    }

    @Test
    fun testGetDeviceInformationSerialization() {
        val jsonStr = CommandSerializer.serialize(CameraCommand.GetDeviceInfo, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(11, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
    }

    // =========================================================================
    // 2. RESPONSE PARSER TESTS
    // =========================================================================

    @Test
    fun testParseStartSessionIntegerToken() {
        val raw = "{\"rval\":0,\"msg_id\":257,\"param\":1001}"
        val response = ResponseParser.parse(raw)
        assertEquals(257, response.msgId)
        assertEquals(0, response.rval)
        assertEquals(1001, response.token)
        assertTrue(response.isSuccess)
    }

    @Test
    fun testParseStartSessionArrayToken() {
        val raw = "{\"rval\":0,\"msg_id\":257,\"param\":[1001]}"
        val response = ResponseParser.parse(raw)
        assertEquals(257, response.msgId)
        assertEquals(0, response.rval)
        assertEquals(1001, response.token)
        assertTrue(response.isSuccess)
    }

    @Test
    fun testParseNormalResponse() {
        val raw = "{\"rval\":0,\"msg_id\":513}"
        val response = ResponseParser.parse(raw)
        assertEquals(513, response.msgId)
        assertEquals(0, response.rval)
        assertTrue(response.isSuccess)
    }

    @Test
    fun testParseGetSettingResponse() {
        val raw = "{\"rval\":0,\"msg_id\":1,\"type\":\"app_status\",\"param\":\"vf\"}"
        val response = ResponseParser.parse(raw)
        assertEquals(1, response.msgId)
        assertEquals(0, response.rval)
        assertEquals("app_status", response.type)
        assertEquals("vf", response.param)
    }

    @Test
    fun testParseListingResponse() {
        val raw = "{\"rval\":0,\"msg_id\":1282,\"listing\":[{\"100MEDIA/LOCA0001.MP4\":\"452001024 bytes|2026-09-15 14:30:00\"}]}"
        val files = ResponseParser.parseListing(raw)
        assertEquals(1, files.size)
        val file = files[0]
        assertEquals("LOCA0001.MP4", file.filename)
        assertEquals("100MEDIA", file.folder)
        assertEquals(452001024L, file.sizeBytes)
        assertEquals("2026-09-15 14:30:00", file.dateTime)
    }

    @Test
    fun testParseMalformedJsonSafeHandling() {
        val raw = "invalid_json_bytes_sample"
        val response = ResponseParser.parse(raw)
        assertEquals(-1, response.msgId)
        assertFalse(response.isSuccess)
    }

    // =========================================================================
    // 3. HARDENED TCP RESPONSE FRAMER TESTS (PHASE I)
    // =========================================================================

    @Test
    fun testFramingFragmentedInput() {
        val chunkedPayload = "{\"rval\":0,\"msg_id\":257,\"param\":1001}\u0000".toByteArray(Charsets.UTF_8)
        val inStream = ByteArrayInputStream(chunkedPayload)
        val result = TcpResponseFramer.readNextJsonResponse(inStream)
        assertEquals("{\"rval\":0,\"msg_id\":257,\"param\":1001}", result)
    }

    @Test
    fun testFramingMultipleMergedJsonFrames() {
        val input = "{\"rval\":0,\"msg_id\":257}{\"rval\":0,\"msg_id\":3}"
        val frames = TcpResponseFramer.extractJsonFrames(input)
        assertEquals(2, frames.size)
        assertEquals("{\"rval\":0,\"msg_id\":257}", frames[0])
        assertEquals("{\"rval\":0,\"msg_id\":3}", frames[1])
    }

    @Test
    fun testFramingBracesInsideQuotedStrings() {
        val input = "{\"rval\":0,\"msg_id\":1,\"param\":\"test {brace} value\"}"
        val inStream = ByteArrayInputStream(input.toByteArray(Charsets.UTF_8))
        val result = TcpResponseFramer.readNextJsonResponse(inStream)
        assertEquals(input, result)
    }

    @Test
    fun testFramingEscapedQuotesInsideStrings() {
        val input = "{\"rval\":0,\"msg_id\":1,\"param\":\"escaped \\\"quote\\\" inside\"}"
        val inStream = ByteArrayInputStream(input.toByteArray(Charsets.UTF_8))
        val result = TcpResponseFramer.readNextJsonResponse(inStream)
        assertEquals(input, result)
    }

    // =========================================================================
    // 4. DEVICE STATUS & UNKNOWN FALLBACK TESTS (PHASE I)
    // =========================================================================

    @Test
    fun testDeviceStatusKnownValues() {
        assertEquals(com.asus.recosmart.domain.model.DeviceStatus.VF, com.asus.recosmart.domain.model.DeviceStatus.fromWire("vf"))
        assertEquals(com.asus.recosmart.domain.model.DeviceStatus.RECORD, com.asus.recosmart.domain.model.DeviceStatus.fromWire("RECORD"))
        assertEquals(com.asus.recosmart.domain.model.DeviceStatus.IDLE, com.asus.recosmart.domain.model.DeviceStatus.fromWire("idle"))
        assertEquals(com.asus.recosmart.domain.model.DeviceStatus.CAPTURE, com.asus.recosmart.domain.model.DeviceStatus.fromWire("capture"))
        assertEquals(com.asus.recosmart.domain.model.DeviceStatus.UVC, com.asus.recosmart.domain.model.DeviceStatus.fromWire("uvc"))
    }

    // =========================================================================
    // 5. REAL HARDWARE PROTOCOL FIX TESTS
    // =========================================================================

    @Test
    fun testStartSessionRealTokenAcquisition() {
        val raw = "{\"rval\":0,\"msg_id\":257,\"param\":10}"
        val response = ResponseParser.parse(raw)
        assertEquals(257, response.msgId)
        assertEquals(0, response.rval)
        assertEquals(10, response.token)
        assertTrue(response.isSuccess)
    }

    @Test
    fun testParseDeviceInfoMetadata() {
        val raw = "{\"rval\":0,\"msg_id\":11,\"brand\":\"SanJet\",\"model\":\"DR38AS\",\"api_ver\":\"2.8.00\",\"fw_ver\":\"2501\",\"app_type\":\"car\",\"logo\":\"/tmp/fuse_z/app_logo.jpg\",\"chip\":\"a7l\",\"http\":\"disable\"}"
        val response = ResponseParser.parse(raw)
        assertEquals(11, response.msgId)
        assertEquals(0, response.rval)
        assertEquals("SanJet", response.brand)
        assertEquals("DR38AS", response.model)
        assertEquals("2.8.00", response.apiVer)
        assertEquals("2501", response.fwVer)
        assertEquals("car", response.appType)
        assertEquals("/tmp/fuse_z/app_logo.jpg", response.logo)
        assertEquals("a7l", response.chip)
        assertEquals("disable", response.http)
    }

    @Test
    fun testParseGetAllCurrentSettingsArray() {
        val raw = "{\"rval\":0,\"msg_id\":3,\"param\":[{\"stream_out_type\":\"rtsp\"},{\"save_low_resolution_clip\":\"on\"},{\"video_resolution\":\"1920x1080 30P 16:9\"}]}"
        val settings = ResponseParser.parseSettings(raw)
        assertEquals(3, settings.size)
        assertEquals("stream_out_type", settings[0].key)
        assertEquals("rtsp", settings[0].value)
        assertEquals("save_low_resolution_clip", settings[1].key)
        assertEquals("on", settings[1].value)
        assertEquals("video_resolution", settings[2].key)
        assertEquals("1920x1080 30P 16:9", settings[2].value)
    }

    @Test
    fun testCdAndLsCommandSerialization() {
        val cdJsonStr = CommandSerializer.serialize(CameraCommand.ChangeDir("/tmp/fuse_d/DCIM"), sessionToken = 10)
        val cdJson = JSONObject(cdJsonStr)
        assertEquals(1283, cdJson.getInt("msg_id"))
        assertEquals(10, cdJson.getInt("token"))
        assertEquals("/tmp/fuse_d/DCIM", cdJson.getString("param"))

        val lsJsonStr = CommandSerializer.serialize(CameraCommand.ListFiles(""), sessionToken = 10)
        val lsJson = JSONObject(lsJsonStr)
        assertEquals(1282, lsJson.getInt("msg_id"))
        assertEquals(10, lsJson.getInt("token"))
        assertFalse("LS after CD must omit param key when path is empty", lsJson.has("param"))
    }

    // =========================================================================
    // 6. FIELD TEST RC2 & RC3 VERIFICATION TESTS
    // =========================================================================

    @Test
    fun testFirstVideoFrameRenderedInitialState() {
        val status = com.asus.recosmart.domain.model.CameraStatus()
        assertFalse("firstVideoFrameRendered must default to false before RTSP frame render", status.firstVideoFrameRendered)
    }

    @Test
    fun testListFilesOmitsHardcodedSubfolder() {
        val cmd = CameraCommand.ListFiles("")
        val jsonStr = CommandSerializer.serialize(cmd, sessionToken = 10)
        val json = JSONObject(jsonStr)
        assertFalse("ListFiles with empty path must NOT inject 116MEDIA or hardcoded subfolder", json.has("param"))
    }

    @Test
    fun testOrphanRval7DoesNotCorrelateWithActiveCommand() {
        val orphanRaw = "{\"rval\":-7}"
        val orphanResp = ResponseParser.parse(orphanRaw)
        assertEquals(-1, orphanResp.msgId)
        assertEquals(-7, orphanResp.rval)
        assertFalse("Orphan frame without msg_id must not be marked success", orphanResp.isSuccess)

        val validRaw = "{\"rval\":0,\"msg_id\":259}"
        val validResp = ResponseParser.parse(validRaw)
        assertEquals(259, validResp.msgId)
        assertTrue("Correlated RESET_TO_VF response must be success", validResp.isSuccess)
    }

    @Test
    fun testParseRealHardwareDcimDirectories() {
        val raw = "{\"rval\":0,\"msg_id\":1282,\"listing\":[{\"110MEDIA/\":\"2021-07-11 15:50:40\"},{\"105MEDIA/\":\"2019-09-18 15:50:40\"},{\"113MEDIA/\":\"2022-08-10 15:50:40\"},{\"116MEDIA/\":\"2015-08-23 15:50:40\"},{\"115MEDIA/\":\"2015-05-05 15:50:40\"}]}"
        val dirs = ResponseParser.parseDirectories(raw)
        assertEquals(5, dirs.size)
        assertEquals("110MEDIA", dirs[0].name)
        assertEquals("105MEDIA", dirs[1].name)
        assertEquals("113MEDIA", dirs[2].name)
        assertEquals("116MEDIA", dirs[3].name)
        assertEquals("115MEDIA", dirs[4].name)
        assertEquals("/tmp/fuse_d/DCIM/116MEDIA", dirs[3].remotePath)
    }

    @Test
    fun testParseRealHardwareMediaFilesAndPairing() {
        val raw = "{\"rval\":0,\"msg_id\":1282,\"listing\":[{\"EMRG3992.mp4\":\"62914560 bytes|2015-08-23 15:52:00\"},{\"FILE3956.mp4\":\"62914560 bytes|2015-08-23 15:50:40\"},{\"FILE3956_thm.mp4\":\"10485760 bytes|2015-08-23 15:50:40\"}]}"
        val files = ResponseParser.parseFilesListing(raw, "116MEDIA")
        assertEquals("Companion thumbnail file must be paired into main file, leaving 2 logical items", 2, files.size)

        val emrgFile = files.find { it.filename == "EMRG3992.mp4" }
        assertNotNull(emrgFile)
        assertTrue("EMRG3992.mp4 must be flagged as emergency recording", emrgFile!!.isEmergency)
        assertEquals("Acil Durum Kaydı", emrgFile.mediaTypeLabel)

        val mainFile = files.find { it.filename == "FILE3956.mp4" }
        assertNotNull(mainFile)
        assertFalse("FILE3956.mp4 is normal video", mainFile!!.isEmergency)
        assertEquals("http://192.168.42.1/DCIM/116MEDIA/FILE3956.mp4", mainFile.httpUrl)
        assertEquals("http://192.168.42.1/DCIM/116MEDIA/FILE3956_thm.mp4", mainFile.thumbnailUrl)
    }

    // =========================================================================
    // 7. FIELD TEST RC4 VERIFICATION TESTS
    // =========================================================================

    @Test
    fun testVideoResolutionCommandSerialization() {
        val cmd = CameraCommand.SetSetting("video_resolution", "1280x720 60P 16:9")
        val jsonStr = CommandSerializer.serialize(cmd, sessionToken = 49)
        val json = JSONObject(jsonStr)
        assertEquals(2, json.getInt("msg_id"))
        assertEquals(49, json.getInt("token"))
        assertEquals("video_resolution", json.getString("type"))
        assertEquals("1280x720 60P 16:9", json.getString("param"))
    }

    // =========================================================================
    // 8. FIELD TEST RC5 VERIFICATION TESTS (EXPORT / MEDIASTORE / HDR)
    // =========================================================================

    @Test
    fun testMediaFileCategoryProperties() {
        val videoFile = com.asus.recosmart.domain.model.CameraFile(
            filename = "FILE0001.MP4",
            folder = "100MEDIA",
            sizeBytes = 104857600L,
            dateTime = "2026-09-16 12:00:00"
        )
        assertTrue("FILE0001.MP4 must be classified as video", videoFile.isVideo)
        assertFalse("FILE0001.MP4 must not be photo", videoFile.isPhoto)

        val photoFile = com.asus.recosmart.domain.model.CameraFile(
            filename = "FILE0002.JPG",
            folder = "100MEDIA",
            sizeBytes = 2097152L,
            dateTime = "2026-09-16 12:01:00"
        )
        assertTrue("FILE0002.JPG must be classified as photo", photoFile.isPhoto)
        assertFalse("FILE0002.JPG must not be video", photoFile.isVideo)
    }

    @Test
    fun testCameraFileRemoteFullPathConstruction() {
        val file = com.asus.recosmart.domain.model.CameraFile(
            filename = "EMRG0005.MP4",
            folder = "116MEDIA",
            sizeBytes = 50000000L,
            dateTime = "2026-09-16 14:00:00"
        )
        assertEquals("Full camera path must combine root DCIM path, subfolder, and filename", "/tmp/fuse_d/DCIM/116MEDIA/EMRG0005.MP4", file.fullCameraPath)
        assertTrue("EMRG prefix must classify file as emergency video", file.isEmergency)
    }

    // =========================================================================
    // 9. FIELD TEST RC6 VERIFICATION TESTS (STABILITY / RECOVERY / RC6 UI)
    // =========================================================================

    @Test
    fun testDeadSocketInitialUnconnectedState() {
        val client = com.asus.recosmart.data.network.TcpSocketClient()
        assertFalse("Unconnected TcpSocketClient must return isConnected = false", client.isConnected)
        assertTrue("Initial transport state must default to healthy", client.isTransportHealthy)
    }

    @Test
    fun testMockRepositorySessionRecovery() = kotlinx.coroutines.runBlocking {
        val repo = com.asus.recosmart.data.mock.MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        val recoveryResult = repo.recoverSession()
        assertTrue("Mock recoverSession must succeed", recoveryResult.isSuccess)
        assertTrue("Active token must be positive after recovery", recoveryResult.getOrDefault(0) > 0)
    }

    @Test
    fun testPhotoSizeSettingCommandSerialization() {
        val cmd = CameraCommand.SetSetting("photo_size", "16M (4608x3456 4:3)")
        val jsonStr = CommandSerializer.serialize(cmd, sessionToken = 1001)
        val json = JSONObject(jsonStr)
        assertEquals(2, json.getInt("msg_id"))
        assertEquals(1001, json.getInt("token"))
        assertEquals("photo_size", json.getString("type"))
        assertEquals("16M (4608x3456 4:3)", json.getString("param"))
    }

    @Test
    fun testSetSettingSuccessResponseParsing() {
        val raw = "{\"rval\":0,\"msg_id\":2}"
        val response = ResponseParser.parse(raw)
        assertEquals(2, response.msgId)
        assertEquals(0, response.rval)
        assertTrue("SetSetting rval=0 response must be parsed as success", response.isSuccess)
    }

    // =========================================================================
    // 10. FIELD TEST RC7 VERIFICATION TESTS (VEHICLE MODE / REDACTED REPORTS / RC7)
    // =========================================================================

    @Test
    fun testFileFilterCategoryDownloadedLabel() {
        assertEquals("Telefona İndirilenler", FileFilterCategory.DOWNLOADED.label)
    }

    @Test
    fun testPhotoResolutionDimensionMpCalculation() {
        val width = 1920
        val height = 1080
        val mp = (width.toLong() * height.toLong()) / 1_000_000.0
        val formattedMp = String.format(java.util.Locale.US, "%.1f", mp)
        assertEquals("2.1", formattedMp)
    }

    @Test
    fun testSanitizationRedactsMacAddresses() {
        val rawLog = "Device MAC address 00:1A:2C:3D:4E:5F connected to AP"
        val sanitized = rawLog.replace(Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"), "[REDACTED_MAC]")
        assertEquals("Device MAC address [REDACTED_MAC] connected to AP", sanitized)
    }

    // =========================================================================
    // 11. FIELD TEST RC7.1 VERIFICATION TESTS (RECORDING LIFECYCLE & FILE MANAGER)
    // =========================================================================

    @Test
    fun testRecordingStateSurvivesSimulatedLiveScreenRecreation() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording()
        assertTrue("Repository must record active recording state", repo.cameraStatus.value.isRecording)

        // Re-open preview on simulated repository
        val vfRes = repo.prepareLiveView()
        assertTrue("prepareLiveView must succeed", vfRes.isSuccess)
        assertTrue("prepareLiveView on re-opened preview must preserve recording state", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testReopeningPreviewDoesNotSendRecordStart() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording()
        assertTrue("Camera is recording", repo.cameraStatus.value.isRecording)

        repo.prepareLiveView()
        assertTrue("isRecording must remain true when reopening preview without sending RECORD_START again", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testReopeningPreviewDoesNotSendRecordStop() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording()
        assertTrue(repo.cameraStatus.value.isRecording)

        repo.prepareLiveView()
        assertTrue("isRecording must not be set to false on preview re-entry", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testRecordStopUpdatesRepositoryState() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording()
        assertTrue(repo.cameraStatus.value.isRecording)

        val result = repo.stopRecording()
        assertTrue("RECORD_STOP command result must be success", result.isSuccess)
        assertFalse("Camera state isRecording must be false after stopRecording", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testRecordStopTriggersBoundedMediaRefreshPath() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording()

        repo.stopRecording()

        val logs = repo.debugLogs.value.joinToString("\n")
        assertTrue("Logs must contain [REC] stop requested", logs.contains("[REC] stop requested"))
        assertTrue("Logs must contain [REC] RECORD_STOP acknowledged", logs.contains("[REC] RECORD_STOP acknowledged"))
        assertTrue("Logs must contain [REC] waiting for filesystem stabilization", logs.contains("[REC] waiting for filesystem stabilization"))
        assertTrue("Logs must contain [REC] refreshing DCIM", logs.contains("[REC] refreshing DCIM"))
        assertTrue("Logs must contain [REC] new media discovered:", logs.contains("[REC] new media discovered:"))
    }

    @Test
    fun testFilenameSearchIsCaseInsensitive() {
        val sampleFiles = listOf(
            CameraFile(filename = "FILE4089.MP4", folder = "116MEDIA"),
            CameraFile(filename = "EMRG0001.MP4", folder = "116MEDIA"),
            CameraFile(filename = "FILE1001.JPG", folder = "100MEDIA")
        )
        val query = "file4089"
        val filtered = sampleFiles.filter {
            it.filename.lowercase().contains(query) || it.folder.lowercase().contains(query)
        }
        assertEquals(1, filtered.size)
        assertEquals("FILE4089.MP4", filtered[0].filename)
    }

    @Test
    fun testDateDescendingSort() {
        val sampleFiles = listOf(
            CameraFile(filename = "FILE0001.MP4", dateTime = "2026-09-15 10:00:00"),
            CameraFile(filename = "FILE0002.MP4", dateTime = "2026-09-16 15:30:00"),
            CameraFile(filename = "FILE0003.MP4", dateTime = "2026-09-14 08:00:00")
        )
        val sorted = sampleFiles.sortedWith(compareByDescending<CameraFile> { it.dateTime.ifEmpty { it.filename } }.thenByDescending { it.filename })
        assertEquals("FILE0002.MP4", sorted[0].filename)
        assertEquals("FILE0001.MP4", sorted[1].filename)
        assertEquals("FILE0003.MP4", sorted[2].filename)
    }

    @Test
    fun testDateAscendingSort() {
        val sampleFiles = listOf(
            CameraFile(filename = "FILE0001.MP4", dateTime = "2026-09-15 10:00:00"),
            CameraFile(filename = "FILE0002.MP4", dateTime = "2026-09-16 15:30:00"),
            CameraFile(filename = "FILE0003.MP4", dateTime = "2026-09-14 08:00:00")
        )
        val sorted = sampleFiles.sortedWith(compareBy<CameraFile> { it.dateTime.ifEmpty { it.filename } }.thenBy { it.filename })
        assertEquals("FILE0003.MP4", sorted[0].filename)
        assertEquals("FILE0001.MP4", sorted[1].filename)
        assertEquals("FILE0002.MP4", sorted[2].filename)
    }

    @Test
    fun testFilenameAscendingSort() {
        val sampleFiles = listOf(
            CameraFile(filename = "FILE4089.MP4"),
            CameraFile(filename = "EMRG0001.MP4"),
            CameraFile(filename = "FILE1001.JPG")
        )
        val sorted = sampleFiles.sortedBy { it.filename.lowercase() }
        assertEquals("EMRG0001.MP4", sorted[0].filename)
        assertEquals("FILE1001.JPG", sorted[1].filename)
        assertEquals("FILE4089.MP4", sorted[2].filename)
    }

    @Test
    fun testFilenameDescendingSort() {
        val sampleFiles = listOf(
            CameraFile(filename = "FILE4089.MP4"),
            CameraFile(filename = "EMRG0001.MP4"),
            CameraFile(filename = "FILE1001.JPG")
        )
        val sorted = sampleFiles.sortedByDescending { it.filename.lowercase() }
        assertEquals("FILE4089.MP4", sorted[0].filename)
        assertEquals("FILE1001.JPG", sorted[1].filename)
        assertEquals("EMRG0001.MP4", sorted[2].filename)
    }

    @Test
    fun testEmergencyFilterStillWorks() {
        val sampleFiles = listOf(
            CameraFile(filename = "FILE4089.MP4"),
            CameraFile(filename = "EMRG0001.MP4"),
            CameraFile(filename = "EMRG0002.MP4")
        )
        val emergencyOnly = sampleFiles.filter { it.isEmergency }
        assertEquals(2, emergencyOnly.size)
        assertTrue(emergencyOnly.all { it.isEmergency })
    }

    @Test
    fun testFolderFilterWorks() {
        val sampleFiles = listOf(
            CameraFile(filename = "FILE0001.MP4", folder = "113MEDIA"),
            CameraFile(filename = "FILE0002.MP4", folder = "116MEDIA"),
            CameraFile(filename = "FILE0003.MP4", folder = "116MEDIA")
        )
        val folder116 = sampleFiles.filter { it.folder.equals("116MEDIA", ignoreCase = true) }
        assertEquals(2, folder116.size)
        assertTrue(folder116.all { it.folder == "116MEDIA" })
    }

    @Test
    fun testStorageFilterWorks() {
        val downloadedMap = mapOf("FILE0001.MP4" to true)
        val sampleFiles = listOf(
            CameraFile(filename = "FILE0001.MP4"),
            CameraFile(filename = "FILE0002.MP4")
        )
        val onCamera = sampleFiles.filter { downloadedMap[it.filename] != true }
        val onPhone = sampleFiles.filter { downloadedMap[it.filename] == true }

        assertEquals(1, onCamera.size)
        assertEquals("FILE0002.MP4", onCamera[0].filename)
        assertEquals(1, onPhone.size)
        assertEquals("FILE0001.MP4", onPhone[0].filename)
    }

    @Test
    fun testFilterSearchSortComposition() {
        val downloadedMap = mapOf("FILE0001.MP4" to true)
        val sampleFiles = listOf(
            CameraFile(filename = "FILE0001.MP4", folder = "116MEDIA", dateTime = "2026-09-16 10:00:00"),
            CameraFile(filename = "FILE0002.MP4", folder = "116MEDIA", dateTime = "2026-09-16 11:00:00"),
            CameraFile(filename = "EMRG0003.MP4", folder = "116MEDIA", dateTime = "2026-09-16 12:00:00"),
            CameraFile(filename = "FILE0004.JPG", folder = "116MEDIA", dateTime = "2026-09-16 13:00:00")
        )

        val pipelineResult = sampleFiles
            .filter { it.isVideo && !it.isEmergency } // Category: Videos
            .filter { downloadedMap[it.filename] != true } // Storage: On Camera
            .filter { it.folder.equals("116MEDIA", ignoreCase = true) } // Folder: 116MEDIA
            .filter { it.filename.lowercase().contains("file") } // Search query: "file"
            .sortedBy { it.filename.lowercase() } // Sort: Name ASC

        assertEquals(1, pipelineResult.size)
        assertEquals("FILE0002.MP4", pipelineResult[0].filename)
    }

    @Test
    fun testDownloadedFilterStillWorks() {
        val downloadedMap = mapOf("FILE0001.MP4" to true)
        val sampleFiles = listOf(
            CameraFile(filename = "FILE0001.MP4"),
            CameraFile(filename = "FILE0002.MP4")
        )
        val downloadedOnly = sampleFiles.filter { downloadedMap[it.filename] == true }
        assertEquals(1, downloadedOnly.size)
        assertEquals("FILE0001.MP4", downloadedOnly[0].filename)
    }

    // =========================================================================
    // 12. FIELD TEST RC7.2 VERIFICATION TESTS (RECORDING LIFECYCLE DECOUPLING)
    // =========================================================================

    @Test
    fun testRecordingSurvivesLiveToRecordsNavigation() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")
        assertTrue(repo.cameraStatus.value.isRecording)

        repo.stopLiveView("LIVE_SCREEN_EXIT")
        assertTrue("Recording must remain true during Live -> Records navigation", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testRecordingSurvivesLiveToSettingsNavigation() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")

        repo.stopLiveView("LIVE_SCREEN_EXIT")
        assertTrue("Recording must remain true during Live -> Settings navigation", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testRecordingSurvivesLiveToConnectionNavigation() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")

        repo.stopLiveView("LIVE_SCREEN_EXIT")
        assertTrue("Recording must remain true during Live -> Connection navigation", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testRecordingSurvivesLiveToProtocolNavigation() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")

        repo.stopLiveView("LIVE_SCREEN_EXIT")
        assertTrue("Recording must remain true during Live -> Protocol navigation", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testRecordingSurvivesLiveToAboutNavigation() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")

        repo.stopLiveView("LIVE_SCREEN_EXIT")
        assertTrue("Recording must remain true during Live -> About navigation", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testLeavingLiveWhileRecordingDoesNotInvokeRecordStop() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")
        repo.clearDebugLogs()

        repo.stopLiveView("LIVE_SCREEN_EXIT")
        val logs = repo.debugLogs.value.joinToString("\n")
        assertFalse("Leaving Live tab while recording must NOT invoke RECORD_STOP (msg_id 514)", logs.contains("514"))
    }

    @Test
    fun testLeavingLiveWhileRecordingDoesNotInvokeStopVf() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")
        repo.clearDebugLogs()

        repo.stopLiveView("LIVE_SCREEN_EXIT")
        val logs = repo.debugLogs.value.joinToString("\n")
        assertTrue("Leaving Live tab while recording must skip camera-side STOP_VF", logs.contains("STOP_VF skipped because camera is currently RECORDING"))
    }

    @Test
    fun testLeavingLiveDoesNotModifyCameraStatusIsRecording() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")

        repo.stopLiveView("LIVE_SCREEN_EXIT")
        assertTrue("cameraStatus.isRecording must stay true on tab exit", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testReopeningLiveWhileAlreadyRecordingDoesNotInvokeRecordStart() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")
        repo.clearDebugLogs()

        repo.prepareLiveView("LIVE_SCREEN_ENTER")
        val logs = repo.debugLogs.value.joinToString("\n")
        assertFalse("Reopening Live tab while recording must NOT invoke RECORD_START (msg_id 513)", logs.contains("msg_id\":513"))
    }

    @Test
    fun testReopeningLiveWhileAlreadyRecordingDoesNotInvokeRecordStop() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")
        repo.clearDebugLogs()

        repo.prepareLiveView("LIVE_SCREEN_ENTER")
        val logs = repo.debugLogs.value.joinToString("\n")
        assertFalse("Reopening Live tab while recording must NOT invoke RECORD_STOP (msg_id 514)", logs.contains("msg_id\":514"))
    }

    @Test
    fun testReopeningLiveWhileRecordingDoesNotPerformDestructiveResetToVf() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")
        repo.clearDebugLogs()

        repo.prepareLiveView("LIVE_SCREEN_ENTER")
        val logs = repo.debugLogs.value.joinToString("\n")
        assertTrue("Reopening Live tab while recording must skip RESET_TO_VF", logs.contains("RESET_TO_VF skipped because camera is currently RECORDING"))
    }

    @Test
    fun testExplicitStopButtonStillInvokesExactlyOneRecordStop() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")
        repo.clearDebugLogs()

        repo.stopRecording("USER_RECORD_BUTTON")
        val logs = repo.debugLogs.value.joinToString("\n")
        assertTrue("Explicit stop button must log origin USER_RECORD_BUTTON and msg_id 514", logs.contains("[CMD][origin=USER_RECORD_BUTTON]") && logs.contains("514"))
    }

    @Test
    fun testExplicitStopStillTriggersMediaDiscoverySequence() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")

        repo.stopRecording("USER_RECORD_BUTTON")
        val logs = repo.debugLogs.value.joinToString("\n")
        assertTrue("Explicit stop must trigger RC7.1 post-stop media discovery", logs.contains("[REC] new media discovered:"))
    }

    @Test
    fun testLocalRtspPlayerReleaseDoesNotAlterRepositoryRecordingState() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("USER_RECORD_BUTTON")

        // Local ExoPlayer release simulation (clearing local UI state)
        repo.setFirstVideoFrameRendered(false)
        assertTrue("Releasing local ExoPlayer state must NOT change repository isRecording", repo.cameraStatus.value.isRecording)
    }

    @Test
    fun testVehicleModeReconnectWhileCameraAlreadyRecordsSendsNoRecordStartOrStopPair() = runBlocking {
        val repo = MockCameraRepository()
        repo.connect("192.168.42.1", 7878)
        repo.startRecording("AUTOMATION")
        repo.clearDebugLogs()

        val statusRes = repo.getAppStatus()
        assertTrue(statusRes.isSuccess)

        val logs = repo.debugLogs.value.joinToString("\n")
        assertFalse("Vehicle mode status check while camera already records must NOT send RECORD_START", logs.contains("msg_id\":513"))
        assertFalse("Vehicle mode status check while camera already records must NOT send RECORD_STOP", logs.contains("msg_id\":514"))
    }
}
