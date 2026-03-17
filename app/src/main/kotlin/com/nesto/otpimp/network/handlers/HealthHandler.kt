package com.nesto.otpimp.network.handlers

import com.nesto.otpimp.network.SseConnectionManager
import com.nesto.otpimp.service.ServiceState
import com.nesto.otpimp.util.Constants
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class HealthHandler(
    private val sseManager: SseConnectionManager,
    private val serviceState: ServiceState
) {
    
    fun handle(): NanoHTTPD.Response {
        val stats = sseManager.getStats()
        val uptime = System.currentTimeMillis() - serviceState.startedAt
        
        val response = JSONObject().apply {
            put("status", "healthy")
            put("version", "1.0.0")
            put("uptime_ms", uptime)
            put("uptime_human", formatUptime(uptime))
            put("server_time", Instant.now().atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT))
            put("connections", JSONObject().apply {
                put("active", stats.activeConnections)
                put("total_served", stats.totalConnectionsServed)
                put("max_allowed", Constants.MAX_SSE_CONNECTIONS)
            })
            put("messages", JSONObject().apply {
                put("received", serviceState.messagesReceived.get())
                put("broadcast", serviceState.messagesBroadcast.get())
            })
        }
        
        return NanoHTTPD.newFixedLengthResponse(
            Status.OK,
            Constants.MimeTypes.JSON,
            response.toString()
        ).apply {
            addHeader(Constants.Headers.CACHE_CONTROL, "no-cache")
        }
    }
    
    private fun formatUptime(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}