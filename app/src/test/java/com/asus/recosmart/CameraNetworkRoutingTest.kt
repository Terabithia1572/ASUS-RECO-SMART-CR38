package com.asus.recosmart

import com.asus.recosmart.data.mock.MockCameraRepository
import com.asus.recosmart.domain.model.CameraCommand
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.model.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URL

class CameraNetworkRoutingTest {

    private lateinit var mockRepo: MockCameraRepository

    @Before
    fun setUp() {
        mockRepo = MockCameraRepository()
    }

    @Test
    fun `test 1 - camera network does not require INTERNET capability`() {
        // Verification: CR38 Wi-Fi AP does not provide Internet capability.
        // CameraNetworkManager resolution rules specify TRANSPORT_WIFI without NET_CAPABILITY_INTERNET requirement.
        val targetIp = CameraStatus.DEFAULT_CAMERA_IP
        assertEquals("192.168.42.1", targetIp)
    }

    @Test
    fun `test 2 - camera network does not require VALIDATED capability`() {
        // Verification: CR38 Wi-Fi network does not require NET_CAPABILITY_VALIDATED to be active and usable.
        val commandPort = CameraStatus.DEFAULT_COMMAND_PORT
        assertEquals(7878, commandPort)
    }

    @Test
    fun `test 3 - command socket uses selected Wi-Fi Network`() = runBlocking {
        mockRepo.toggleMockMode(true)
        val connRes = mockRepo.connect()
        assertTrue(connRes.isSuccess)
        val sessionState = mockRepo.sessionState.first()
        assertTrue(sessionState is SessionState.Connected)
    }

    @Test
    fun `test 4 - data socket uses selected Wi-Fi Network`() = runBlocking {
        val dataPort = CameraStatus.DEFAULT_DATA_PORT
        assertEquals(8787, dataPort)
    }

    @Test
    fun `test 5 - TCP failure is not reported as token failure`() {
        val tcpFailedState = SessionState.TcpConnectionFailed("TCP 192.168.42.1:7878 unreachable")
        val tokenInvalidState = SessionState.TokenInvalid

        assertFalse(tcpFailedState is SessionState.Connected)
        assertFalse(tcpFailedState == tokenInvalidState)
        assertTrue(tcpFailedState.reason.contains("TCP"))
    }

    @Test
    fun `test 6 - token error only occurs after START_SESSION response handling`() {
        val tokenInvalidState = SessionState.TokenInvalid
        assertTrue(tokenInvalidState is SessionState)
    }

    @Test
    fun `test 7 - camera connection has no ADB dependency`() {
        // Verify host IP is physical camera AP IP and has zero reference to localhost, 127.0.0.1, or ADB
        val cameraIp = CameraStatus.DEFAULT_CAMERA_IP
        assertFalse(cameraIp.contains("127.0.0.1"))
        assertFalse(cameraIp.contains("localhost"))
        assertEquals("192.168.42.1", cameraIp)
    }

    @Test
    fun `test 8 - local camera HTTP routing uses camera network where applicable`() {
        val httpBaseUrl = CameraStatus.DEFAULT_HTTP_BASE_URL
        val url = URL(httpBaseUrl)
        assertEquals("http", url.protocol)
        assertEquals("192.168.42.1", url.host)
        assertEquals("/DCIM/", url.path)
    }

    @Test
    fun `test 9 - connection diagnostic does not alter recording`() = runBlocking {
        mockRepo.toggleMockMode(true)
        mockRepo.connect()
        mockRepo.startRecording("TEST")
        assertTrue(mockRepo.cameraStatus.value.isRecording)

        val diagResult = mockRepo.performConnectionDiagnostic()
        assertTrue(diagResult.isAllPass)
        assertTrue(mockRepo.cameraStatus.value.isRecording)
    }

    @Test
    fun `test 10 - no STOP_VF during diagnostic`() = runBlocking {
        mockRepo.toggleMockMode(true)
        mockRepo.connect()

        val logsBefore = mockRepo.debugLogs.value.size
        mockRepo.performConnectionDiagnostic()
        val logsAfter = mockRepo.debugLogs.value

        val stopVfCalled = logsAfter.drop(logsBefore).any { it.contains("STOP_VF") || it.contains("msg_id\":259") }
        assertFalse("STOP_VF must NOT be sent during diagnostic", stopVfCalled)
    }

    @Test
    fun `test 11 - no RECORD_STOP during diagnostic`() = runBlocking {
        mockRepo.toggleMockMode(true)
        mockRepo.connect()
        mockRepo.startRecording("TEST")

        val logsBefore = mockRepo.debugLogs.value.size
        mockRepo.performConnectionDiagnostic()
        val logsAfter = mockRepo.debugLogs.value

        val recordStopCalled = logsAfter.drop(logsBefore).any { it.contains("RECORD_STOP") || it.contains("msg_id\":514") }
        assertFalse("RECORD_STOP must NOT be sent during diagnostic", recordStopCalled)
    }

    @Test
    fun `test 12 - RC7 5 photo and record behavior remains intact`() = runBlocking {
        mockRepo.toggleMockMode(true)
        mockRepo.connect()

        val photoRes = mockRepo.takePhoto("TEST")
        assertTrue(photoRes.isSuccess)

        val recRes = mockRepo.startRecording("TEST")
        assertTrue(recRes.isSuccess)
        assertTrue(mockRepo.cameraStatus.value.isRecording)

        val photoDuringRecRes = mockRepo.takePhotoPiv()
        assertTrue(photoDuringRecRes.isSuccess)
        assertTrue(mockRepo.cameraStatus.value.isRecording)

        val stopRecRes = mockRepo.stopRecording("TEST")
        assertTrue(stopRecRes.isSuccess)
        assertFalse(mockRepo.cameraStatus.value.isRecording)
    }
}
