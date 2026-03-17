package com.nesto.otpimp.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    
    private const val TAG = "NetworkUtils"
    
    /**
     * Returns the device's local IP address on the Wi-Fi network.
     * Tries multiple methods for reliability.
     */
    fun getLocalIpAddress(context: Context): String? {
        return try {
            getWifiIpAddress(context) ?: getNetworkInterfaceIpAddress()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get local IP address", e)
            null
        }
    }
    
    @Suppress("DEPRECATION")
    private fun getWifiIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        
        val wifiInfo = wifiManager.connectionInfo ?: return null
        val ipInt = wifiInfo.ipAddress
        
        if (ipInt == 0) return null
        
        return String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )
    }
    
    private fun getNetworkInterfaceIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to get IP from NetworkInterface", e)
            null
        }
    }
    
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    fun getNetworkInfo(context: Context): NetworkInfo {
        val ip = getLocalIpAddress(context)
        val wifiConnected = isWifiConnected(context)
        
        return NetworkInfo(
            ipAddress = ip,
            isWifiConnected = wifiConnected,
            serverUrl = ip?.let { "http://$it:${Constants.SERVER_PORT}" }
        )
    }
    
    data class NetworkInfo(
        val ipAddress: String?,
        val isWifiConnected: Boolean,
        val serverUrl: String?
    )
}