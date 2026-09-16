package com.asus.recosmart.data.network

import com.asus.recosmart.data.protocol.CommandSerializer
import com.asus.recosmart.data.protocol.ResponseParser
import com.asus.recosmart.domain.model.CameraCommand
import com.asus.recosmart.domain.model.CameraResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceNotification(
    val msgId: Int,
    val type: String?,
    val param: String?,
    val rval: Int,
    val rawResponse: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class TcpSocketClient {

    private var commandSocket: Socket? = null
    private var commandOutputStream: OutputStream? = null
    private var commandInputStream: InputStream? = null

    private var dataSocket: Socket? = null
    private var dataOutputStream: OutputStream? = null

    private val receiveBuffer = ByteArrayOutputStream()
    private val commandMutex = Mutex()

    private var _isTransportHealthy = true
    val isTransportHealthy: Boolean
        get() = _isTransportHealthy

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _notifications = MutableSharedFlow<DeviceNotification>(extraBufferCapacity = 64)
    val notifications: SharedFlow<DeviceNotification> = _notifications.asSharedFlow()

    val isConnected: Boolean
        get() = _isTransportHealthy && commandSocket?.isConnected == true && commandSocket?.isClosed == false

    suspend fun connectCommandSocket(ip: String, commandPort: Int, timeoutMs: Int = 4000): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect()
            log("[REAL CONNECT] Initiating TCP connection to $ip:$commandPort (timeout: ${timeoutMs}ms)...")
            val newSocket = Socket()
            CameraNetworkManager.bindSocketToWifi(newSocket)
            newSocket.connect(InetSocketAddress(ip, commandPort), timeoutMs)
            newSocket.soTimeout = timeoutMs

            commandSocket = newSocket
            commandOutputStream = newSocket.getOutputStream()
            commandInputStream = newSocket.getInputStream()
            receiveBuffer.reset()
            _isTransportHealthy = true

            log("[REAL CONNECT] TCP socket established with $ip:$commandPort (Wi-Fi bound)")
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = "TCP Connection failed to $ip:$commandPort: ${e.localizedMessage}"
            log("[REAL ERROR] $msg")
            disconnect()
            Result.failure(e)
        }
    }

    suspend fun connectDataSocket(ip: String, dataPort: Int, timeoutMs: Int = 4000): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            log("[REAL CONNECT] Opening secondary data socket $ip:$dataPort...")
            val newDataSocket = Socket()
            CameraNetworkManager.bindSocketToWifi(newDataSocket)
            newDataSocket.connect(InetSocketAddress(ip, dataPort), timeoutMs)
            newDataSocket.soTimeout = timeoutMs

            dataSocket = newDataSocket
            dataOutputStream = newDataSocket.getOutputStream()

            log("[REAL CONNECT] Secondary data socket connected to $ip:$dataPort")
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = "Secondary data socket connection notice ($ip:$dataPort): ${e.localizedMessage}"
            log("[REAL ERROR] $msg")
            Result.failure(e)
        }
    }

    suspend fun sendCommand(command: CameraCommand, token: Int): Result<CameraResponse> = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            val outSocket = commandSocket
            val outStream = commandOutputStream
            val inStream = commandInputStream

            if (!_isTransportHealthy || outSocket == null || outStream == null || inStream == null || !outSocket.isConnected || outSocket.isClosed) {
                val err = "Command socket is not connected or transport is unhealthy"
                log("[REAL ERROR] $err")
                return@withContext Result.failure(IllegalStateException(err))
            }

            val startTime = System.currentTimeMillis()
            val host = outSocket.inetAddress?.hostAddress ?: "192.168.42.1"
            val port = outSocket.port

            val expectedMsgId = command.msgId
            val expectedType: String? = when (command) {
                is CameraCommand.GetSetting -> command.paramKey
                is CameraCommand.GetAppStatus -> "app_status"
                is CameraCommand.SetSetting -> command.paramKey
                else -> null
            }

            try {
                val payload = CommandSerializer.serialize(command, token)
                val txBytes = payload.toByteArray(Charsets.UTF_8)
                log("[REAL TX] Host: $host:$port | Cmd: ${command.commandName} | msg_id: ${command.msgId} | token: $token | bytes: ${txBytes.size} | JSON: $payload")

                outStream.write(txBytes)
                outStream.write(0) // Null byte delimiter as used by RECO Smart firmware
                outStream.flush()

                var matchedResponse: CameraResponse? = null
                var readCount = 0
                val maxReads = 8

                while (matchedResponse == null && readCount < maxReads) {
                    readCount++
                    val rawResponse = com.asus.recosmart.data.protocol.TcpResponseFramer.readNextJsonResponse(inStream, receiveBuffer)
                    val elapsedMs = System.currentTimeMillis() - startTime
                    val rxByteCount = rawResponse.toByteArray(Charsets.UTF_8).size
                    val parsed = ResponseParser.parse(rawResponse)

                    val isMsgIdMatch = parsed.msgId == expectedMsgId
                    val isTypeMatch = expectedType == null || parsed.type == expectedType || 
                            (expectedType == "app_status" && (parsed.type == "app_status" || parsed.param == "idle" || parsed.param == "vf" || parsed.param == "RECORD"))

                    val isCorrelatedMatch = isMsgIdMatch && isTypeMatch

                    if (isCorrelatedMatch) {
                        log("[REAL RX] Host: $host:$port | Elapsed: ${elapsedMs}ms | Size: ${rxByteCount}b | RAW: $rawResponse")
                        log("[REAL RESPONSE MATCH] ${command.commandName} satisfied after ${elapsedMs}ms: msg_id=${parsed.msgId}, rval=${parsed.rval}, token=${parsed.token}, param=${parsed.param}, type=${parsed.type}")
                        matchedResponse = parsed
                    } else if (parsed.type != null || (parsed.msgId != -1 && parsed.msgId != expectedMsgId)) {
                        log("[REAL NOTIFY] Asynchronous frame received during ${command.commandName} wait: msg_id=${parsed.msgId}, type=${parsed.type}, param=${parsed.param}, rval=${parsed.rval} | RAW: $rawResponse")
                        _notifications.tryEmit(
                            DeviceNotification(
                                msgId = parsed.msgId,
                                type = parsed.type,
                                param = parsed.param,
                                rval = parsed.rval,
                                rawResponse = rawResponse
                            )
                        )
                    } else {
                        log("[REAL ORPHAN RX] RAW=$rawResponse | rval=${parsed.rval} | No msg_id/type correlation. Ignored for active request ${command.commandName}")
                    }
                }

                val finalResponse = matchedResponse ?: return@withContext Result.failure(
                    java.util.concurrent.TimeoutException("Timed out waiting for correlated response msg_id $expectedMsgId (type=$expectedType)")
                )
                Result.success(finalResponse)
            } catch (e: java.io.EOFException) {
                val elapsedMs = System.currentTimeMillis() - startTime
                log("[REAL ERROR] EOF / Socket closed by camera after ${elapsedMs}ms: ${e.localizedMessage}")
                disconnectInternal()
                Result.failure(IllegalStateException("Socket closed by camera", e))
            } catch (e: java.net.SocketException) {
                val elapsedMs = System.currentTimeMillis() - startTime
                log("[REAL ERROR] Socket error (Broken pipe / reset) after ${elapsedMs}ms: ${e.localizedMessage}")
                disconnectInternal()
                Result.failure(e)
            } catch (e: Exception) {
                val elapsedMs = System.currentTimeMillis() - startTime
                log("[REAL ERROR] Command execution error after ${elapsedMs}ms: ${e.localizedMessage}")
                if (e is java.io.IOException) {
                    disconnectInternal()
                }
                Result.failure(e)
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        _isTransportHealthy = false
        try {
            dataOutputStream?.close()
            dataSocket?.close()
            commandInputStream?.close()
            commandOutputStream?.close()
            commandSocket?.close()
            receiveBuffer.reset()
            log("[REAL DISCONNECT] Socket resources closed successfully")
        } catch (e: Exception) {
            // Ignore close errors
        } finally {
            dataOutputStream = null
            dataSocket = null
            commandInputStream = null
            commandOutputStream = null
            commandSocket = null
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$timestamp] $message"
        val current = _logs.value.toMutableList()
        if (current.size > 300) current.removeAt(0)
        current.add(entry)
        _logs.value = current
    }
}
