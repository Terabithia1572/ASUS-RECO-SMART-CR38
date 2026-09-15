package com.asus.recosmart.protocol

import com.asus.recosmart.data.protocol.CommandSerializer
import com.asus.recosmart.data.protocol.ResponseParser
import com.asus.recosmart.data.protocol.TcpResponseFramer
import com.asus.recosmart.domain.model.CameraCommand
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

    @Test
    fun testDeviceStatusUnknownValueFallback() {
        val unknown = com.asus.recosmart.domain.model.DeviceStatus.fromWire("custom_unrecognized_firmware_state")
        assertEquals(com.asus.recosmart.domain.model.DeviceStatus.UNKNOWN, unknown)
    }
}
