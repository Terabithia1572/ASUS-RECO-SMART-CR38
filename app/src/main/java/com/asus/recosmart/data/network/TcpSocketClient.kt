package com.asus.recosmart.data.network

import com.asus.recosmart.data.protocol.CommandSerializer
import com.asus.recosmart.data.protocol.ResponseParser
import com.asus.recosmart.domain.model.CameraCommand
import com.asus.recosmart.domain.model.CameraResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TcpSocketClient {

    private var commandSocket: Socket? = null
    private var commandOutputStream: OutputStream? = null
    private var commandInputStream: InputStream? = null

    private var dataSocket: Socket? = null
    private var dataOutputStream: OutputStream? = null

    private val receiveBuffer = ByteArrayOutputStream()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    val isConnected: Boolean
        get() = commandSocket?.isConnected == true && commandSocket?.isClosed == false

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
        val outSocket = commandSocket
        val outStream = commandOutputStream
        val inStream = commandInputStream

        if (outSocket == null || outStream == null || inStream == null || !outSocket.isConnected || outSocket.isClosed) {
            val err = "Command socket is not connected"
            log("[REAL ERROR] $err")
            return@withContext Result.failure(IllegalStateException(err))
        }

        val startTime = System.currentTimeMillis()
        val host = outSocket.inetAddress?.hostAddress ?: "192.168.42.1"
        val port = outSocket.port

        try {
            val payload = CommandSerializer.serialize(command, token)
            val txBytes = payload.toByteArray(Charsets.UTF_8)
            log("[REAL TX] Host: $host:$port | Cmd: ${command.commandName} | msg_id: ${command.msgId} | token: $token | bytes: ${txBytes.size} | JSON: $payload")

            outStream.write(txBytes)
            outStream.write(0) // Null byte delimiter as used by RECO Smart firmware
            outStream.flush()

            var matchedResponse: CameraResponse? = null
            var readCount = 0
            val maxReads = 4

            while (matchedResponse == null && readCount < maxReads) {
                readCount++
                val rawResponse = com.asus.recosmart.data.protocol.TcpResponseFramer.readNextJsonResponse(inStream, receiveBuffer)
                val elapsedMs = System.currentTimeMillis() - startTime
                val rxByteCount = rawResponse.toByteArray(Charsets.UTF_8).size
                val parsed = ResponseParser.parse(rawResponse)

                if (parsed.msgId == command.msgId || parsed.msgId == -1) {
                    log("[REAL RX] Host: $host:$port | Elapsed: ${elapsedMs}ms | Size: ${rxByteCount}b | RAW: $rawResponse")
                    log("[REAL RX PARSED] msg_id=${parsed.msgId}, rval=${parsed.rval}, token=${parsed.token}, param=${parsed.param}, type=${parsed.type}")
                    matchedResponse = parsed
                } else {
                    log("[REAL NOTIFY] Unsolicited frame received during ${command.commandName} wait: msg_id=${parsed.msgId}, type=${parsed.type}, param=${parsed.param} | RAW: $rawResponse")
                }
            }

            val finalResponse = matchedResponse ?: return@withContext Result.failure(java.util.concurrent.TimeoutException("Timed out waiting for response msg_id ${command.msgId}"))
            Result.success(finalResponse)
        } catch (e: java.io.EOFException) {
            val elapsedMs = System.currentTimeMillis() - startTime
            log("[REAL ERROR] EOF received after ${elapsedMs}ms (Socket closed by host $host:$port)")
            disconnect()
            Result.failure(IllegalStateException("Socket closed by camera"))
        } catch (e: Exception) {
            val elapsedMs = System.currentTimeMillis() - startTime
            log("[REAL ERROR] Host $host:$port error after ${elapsedMs}ms: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
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
