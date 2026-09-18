package com.asus.recosmart.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.asus.recosmart.RecoSmartApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

object CameraNetworkManager {

    const val TAG = "CameraNetwork"
    const val CAMERA_IP = "192.168.42.1"
    const val COMMAND_PORT = 7878

    private fun getAppContextSafe(): Context? {
        return try {
            RecoSmartApp.instance.applicationContext
        } catch (e: Throwable) {
            null
        }
    }

    private fun safeLogD(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (e: Throwable) {
            // Ignored in unit test environment
        }
    }

    private fun safeLogW(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (e: Throwable) {
            // Ignored in unit test environment
        }
    }

    private fun safeLogE(tag: String, msg: String) {
        try {
            android.util.Log.e(tag, msg)
        } catch (e: Throwable) {
            // Ignored in unit test environment
        }
    }

    /**
     * Resolves the current Wi-Fi network connected to the device without requiring
     * NET_CAPABILITY_INTERNET or NET_CAPABILITY_VALIDATED (crucial for CR38 AP with no Internet).
     */
    fun getCameraWifiNetwork(context: Context? = getAppContextSafe()): Network? {
        if (context == null) return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null

        try {
            @Suppress("DEPRECATION")
            val networks = cm.allNetworks
            for (net in networks) {
                val caps = cm.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    safeLogD(TAG, "[NET] candidate transport=WIFI | internetCapability=$hasInternet | validated=$isValidated | localRouteCandidate=true")
                    return net
                }
            }

            val activeNet = cm.activeNetwork
            if (activeNet != null) {
                val caps = cm.getNetworkCapabilities(activeNet)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    safeLogD(TAG, "[NET] activeNetwork candidate transport=WIFI | internetCapability=$hasInternet | validated=$isValidated | localRouteCandidate=true")
                    return activeNet
                }
            }
        } catch (e: Exception) {
            safeLogW(TAG, "[NET] Error querying network list: ${e.localizedMessage}")
        }

        return null
    }

    /**
     * Instantiates a new TCP Socket explicitly bound to the Wi-Fi network interface.
     * Uses [Network.socketFactory] as primary mechanism, falling back to [Network.bindSocket].
     */
    fun createWifiSocket(context: Context? = getAppContextSafe()): Socket {
        val wifiNetwork = getCameraWifiNetwork(context)
        if (wifiNetwork != null) {
            return try {
                val socket = wifiNetwork.socketFactory.createSocket()
                safeLogD(TAG, "[NET] Created TCP Socket via camera Wi-Fi Network socketFactory")
                socket
            } catch (e: Exception) {
                safeLogW(TAG, "[NET] socketFactory.createSocket failed: ${e.localizedMessage}, falling back to bindSocket")
                val socket = Socket()
                try {
                    wifiNetwork.bindSocket(socket)
                    safeLogD(TAG, "[NET] Explicitly bound TCP Socket to camera Wi-Fi Network interface")
                } catch (bindEx: Exception) {
                    safeLogE(TAG, "[NET] bindSocket error: ${bindEx.localizedMessage}")
                }
                socket
            }
        } else {
            safeLogW(TAG, "[NET] No Wi-Fi Network candidate found; instantiating default unbound Socket()")
            return Socket()
        }
    }

    /**
     * Opens an [HttpURLConnection] for camera-local traffic routed strictly through the Wi-Fi Network interface.
     * Keeps cellular data active for normal Internet traffic.
     */
    fun openWifiHttpConnection(url: URL, context: Context? = getAppContextSafe()): HttpURLConnection {
        val wifiNetwork = getCameraWifiNetwork(context)
        return if (wifiNetwork != null) {
            try {
                safeLogD(TAG, "[HTTP] Opening HTTP connection over camera Wi-Fi Network interface to: $url")
                wifiNetwork.openConnection(url) as HttpURLConnection
            } catch (e: Exception) {
                safeLogW(TAG, "[HTTP] wifiNetwork.openConnection failed (${e.localizedMessage}), fallback to url.openConnection()")
                url.openConnection() as HttpURLConnection
            }
        } else {
            safeLogD(TAG, "[HTTP] No Wi-Fi Network found; opening connection over default route: $url")
            url.openConnection() as HttpURLConnection
        }
    }

    /**
     * Binds a target TCP [socket] to the Wi-Fi network interface connected to the CR38 camera.
     */
    fun bindSocketToWifi(socket: Socket, context: Context? = getAppContextSafe()) {
        try {
            val wifiNetwork = getCameraWifiNetwork(context)
            if (wifiNetwork != null) {
                wifiNetwork.bindSocket(socket)
                safeLogD(TAG, "[NET] Target socket successfully bound to Wi-Fi network interface")
            }
        } catch (e: Exception) {
            safeLogW(TAG, "[NET] Socket binding to Wi-Fi failed: ${e.localizedMessage}")
        }
    }

    /**
     * Binds process network as secondary fallback if requested.
     */
    fun bindProcessToWifi(context: Context? = getAppContextSafe()): Boolean {
        if (context == null) return false
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val wifiNetwork = getCameraWifiNetwork(context)
            if (wifiNetwork != null) {
                cm.bindProcessToNetwork(wifiNetwork)
                safeLogD(TAG, "[NET] Bound process network to CR38 Wi-Fi interface")
                return true
            }
        } catch (e: Exception) {
            safeLogW(TAG, "[NET] Process binding fallback error: ${e.localizedMessage}")
        }
        return false
    }

    /**
     * Performs a lightweight diagnostic TCP connection check to the camera RTSP port (554).
     */
    suspend fun probeRtspSocket(ip: String = CAMERA_IP, port: Int = 554, timeoutMs: Int = 2000): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = createWifiSocket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Performs a pre-flight reachability diagnostic to 192.168.42.1:7878 using Wi-Fi bound socket.
     */
    suspend fun performNetworkPreflight(context: Context? = getAppContextSafe()): NetworkPreflightResult = withContext(Dispatchers.IO) {
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiNetwork = getCameraWifiNetwork(context)
        val caps = if (wifiNetwork != null && cm != null) cm.getNetworkCapabilities(wifiNetwork) else null

        val isWifiConnected = wifiNetwork != null || isNetworkAvailable(cm)
        val wifiValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        var isHostReachable = false
        var isPortOpen = false
        var latencyMs = -1L

        val startTime = System.currentTimeMillis()
        try {
            val testSocket = createWifiSocket(context)
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
            wifiNetworkFound = wifiNetwork != null,
            wifiValidated = wifiValidated,
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
    val wifiNetworkFound: Boolean,
    val wifiValidated: Boolean,
    val isHostReachable: Boolean,
    val isPortOpen: Boolean,
    val latencyMs: Long,
    val targetIp: String,
    val commandPort: Int
)
