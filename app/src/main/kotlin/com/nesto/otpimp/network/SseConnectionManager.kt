package com.nesto.otpimp.network

import com.nesto.otpimp.data.model.OtpMessage
import com.nesto.otpimp.util.Constants
import com.nesto.otpimp.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages Server-Sent Events (SSE) connections and broadcasts.
 */
class SseConnectionManager(
    private val otpStream: SharedFlow<OtpMessage>,
    private val maxConnections: Int = Constants.MAX_SSE_CONNECTIONS,
    private val heartbeatIntervalMs: Long = Constants.SSE_HEARTBEAT_INTERVAL_MS.toLong()
) {
    companion object {
        private const val TAG = "SseConnectionManager"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connections = ConcurrentHashMap<String, SseConnection>()
    private val connectionIdCounter = AtomicLong(0)
    private val activeCount = AtomicInteger(0)
    
    data class SseConnection(
        val id: String,
        val outputStream: OutputStream,
        val connectedAt: Long = System.currentTimeMillis(),
        var lastActivityAt: Long = System.currentTimeMillis(),
        val job: Job
    )
    
    data class Stats(
        val activeConnections: Int,
        val totalConnectionsServed: Long,
        val oldestConnectionAgeMs: Long?
    )
    
    init {
        startBroadcastListener()
        startHeartbeat()
    }
    
    /**
     * Register a new SSE connection.
     * Returns the connection ID or null if max connections reached.
     */
    fun registerConnection(outputStream: OutputStream): String? {
        if (activeCount.get() >= maxConnections) {
            Logger.w(TAG, "Max connections ($maxConnections) reached, rejecting new connection")
            return null
        }
        
        val connectionId = "sse-${connectionIdCounter.incrementAndGet()}"
        
        val job = scope.launch {
            // Keep connection alive until cancelled
            try {
                awaitCancellation()
            } finally {
                removeConnection(connectionId)
            }
        }
        
        val connection = SseConnection(
            id = connectionId,
            outputStream = outputStream,
            job = job
        )
        
        connections[connectionId] = connection
        activeCount.incrementAndGet()
        
        Logger.i(TAG, "New SSE connection: $connectionId (active: ${activeCount.get()})")
        
        // Send initial comment to establish connection
        sendToConnection(connection, ": connected\n\n")
        
        return connectionId
    }
    
    /**
     * Remove a connection (called when client disconnects or on error).
     */
    fun removeConnection(connectionId: String) {
        connections.remove(connectionId)?.let { connection ->
            connection.job.cancel()
            activeCount.decrementAndGet()
            Logger.i(TAG, "SSE connection removed: $connectionId (active: ${activeCount.get()})")
        }
    }
    
    /**
     * Get current connection statistics.
     */
    fun getStats(): Stats {
        val now = System.currentTimeMillis()
        val oldest = connections.values.minOfOrNull { now - it.connectedAt }
        
        return Stats(
            activeConnections = activeCount.get(),
            totalConnectionsServed = connectionIdCounter.get(),
            oldestConnectionAgeMs = oldest
        )
    }
    
    /**
     * Broadcast a message to all connected clients.
     */
    private fun broadcast(message: OtpMessage) {
        val sseEvent = message.toSseEvent()
        val deadConnections = mutableListOf<String>()
        
        connections.forEach { (id, connection) ->
            if (!sendToConnection(connection, sseEvent)) {
                deadConnections.add(id)
            }
        }
        
        // Clean up dead connections
        deadConnections.forEach { removeConnection(it) }
        
        if (connections.isNotEmpty()) {
            Logger.d(TAG, "Broadcast OTP to ${connections.size} clients")
        }
    }
    
    private fun sendToConnection(connection: SseConnection, data: String): Boolean {
        return try {
            connection.outputStream.write(data.toByteArray(Charsets.UTF_8))
            connection.outputStream.flush()
            connection.lastActivityAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to send to ${connection.id}: ${e.message}")
            false
        }
    }
    
    private fun startBroadcastListener() {
        scope.launch {
            otpStream.collect { message ->
                broadcast(message)
            }
        }
    }
    
    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                
                val deadConnections = mutableListOf<String>()
                val heartbeat = ": heartbeat ${System.currentTimeMillis()}\n\n"
                
                connections.forEach { (id, connection) ->
                    if (!sendToConnection(connection, heartbeat)) {
                        deadConnections.add(id)
                    }
                }
                
                deadConnections.forEach { removeConnection(it) }
                
                if (deadConnections.isNotEmpty()) {
                    Logger.d(TAG, "Heartbeat removed ${deadConnections.size} dead connections")
                }
            }
        }
    }
    
    fun shutdown() {
        Logger.i(TAG, "Shutting down SSE manager")
        scope.cancel()
        connections.clear()
        activeCount.set(0)
    }
}