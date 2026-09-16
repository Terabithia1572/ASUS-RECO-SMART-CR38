package com.asus.recosmart.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.asus.recosmart.RecoSmartApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object CameraNetworkManager {

    const val CAMERA_IP = "192.168.42.1"
    const val COMMAND_PORT = 7878

    /**
     * Binds a target TCP [socket] to the Wi-Fi network interface connected to the CR38 camera.
     * Prevents Android from routing camera traffic over Cellular Data when CR38 Wi-Fi has no Internet access.
     */
    fun bindSocketToWifi(socket: Socket, context: Context = RecoSmartApp.instance.applicationContext) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val wifiNetwork = cm.allNetworks.find { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifiNetwork != null) {
                wifiNetwork.bindSocket(socket)
            }
        } catch (e: Exception) {
            // Socket will fallback to default route if network binding fails
        }
    }

    /**
     * Binds the entire process network to the Wi-Fi network interface connected to the CR38 camera.
     * Ensures all sockets (including ExoPlayer RTSP streams) route over Wi-Fi when no Internet is available.
     */
    fun bindProcessToWifi(context: Context = RecoSmartApp.instance.applicationContext): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val wifiNetwork = cm.allNetworks.find { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifiNetwork != null) {
                cm.bindProcessToNetwork(wifiNetwork)
                return true
            }
        } catch (e: Exception) {
            // Process binding fallback
        }
        return false
    }

    /**
     * Performs a lightweight diagnostic TCP connection check to the camera RTSP port (554).
     */
    suspend fun probeRtspSocket(ip: String = CAMERA_IP, port: Int = 554, timeoutMs: Int = 2000): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            bindSocketToWifi(socket)
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Performs a lightweight pre-flight reachability diagnostic to 192.168.42.1:7878.
     */
    suspend fun performNetworkPreflight(context: Context = RecoSmartApp.instance.applicationContext): NetworkPreflightResult = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiNetwork = cm?.allNetworks?.find { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }

        val isWifiConnected = wifiNetwork != null || isNetworkAvailable(cm)
        var isHostReachable = false
        var isPortOpen = false
        var latencyMs = -1L

        val startTime = System.currentTimeMillis()
        try {
            val testSocket = Socket()
            if (wifiNetwork != null) {
                wifiNetwork.bindSocket(testSocket)
            }
            testSocket.connect(InetSocketAddress(CAMERA_IP, COMMAND_PORT), 3000)
            latencyMs = System.currentTimeMillis() - startTime
            isPortOpen = true
            isHostReachable = true
            testSocket.close()
        } catch (e: Exception) {
            isPortOpen = false
            try {
                val address = InetAddress.getByName(CAMERA_IP)
                isHostReachable = address.isReachable(2000)
            } catch (ex: Exception) {
                isHostReachable = false
            }
        }

        NetworkPreflightResult(
            isWifiConnected = isWifiConnected,
            isHostReachable = isHostReachable,
            isPortOpen = isPortOpen,
            latencyMs = latencyMs,
            targetIp = CAMERA_IP,
            commandPort = COMMAND_PORT
        )
    }

    private fun isNetworkAvailable(cm: ConnectivityManager?): Boolean {
        if (cm == null) return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}

data class NetworkPreflightResult(
    val isWifiConnected: Boolean,
    val isHostReachable: Boolean,
    val isPortOpen: Boolean,
    val latencyMs: Long,
    val targetIp: String,
    val commandPort: Int
)
