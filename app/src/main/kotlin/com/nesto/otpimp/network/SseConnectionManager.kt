package com.nesto.otpimp.network

import com.nesto.otpimp.data.model.OtpMessage
import com.nesto.otpimp.util.Constants
import com.nesto.otpimp.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
        val stream: SseOutputStream,  // Changed from OutputStream
        val connectedAt: Long = System.currentTimeMillis(),
        var lastActivityAt: Long = System.currentTimeMillis()
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
     * Create a new SSE connection and return both the ID and the InputStream for NanoHTTPD.
     */
    fun createConnection(): Pair<String, SseOutputStream>? {
        if (activeCount.get() >= maxConnections) {
            Logger.w(TAG, "Max connections ($maxConnections) reached, rejecting new connection")
            return null
        }
        
        val connectionId = "sse-${connectionIdCounter.incrementAndGet()}"
        val stream = SseOutputStream()
        
        val connection = SseConnection(
            id = connectionId,
            stream = stream
        )
        
        connections[connectionId] = connection
        activeCount.incrementAndGet()
        
        Logger.i(TAG, "New SSE connection: $connectionId (active: ${activeCount.get()})")
        
        // Send initial comment to establish connection
        stream.write(": connected\n\n")
        
        return connectionId to stream
    }
    
    fun removeConnection(connectionId: String) {
        connections.remove(connectionId)?.let { connection ->
            connection.stream.closeStream()
            activeCount.decrementAndGet()
            Logger.i(TAG, "SSE connection removed: $connectionId (active: ${activeCount.get()})")
        }
    }
    
    fun getStats(): Stats {
        val now = System.currentTimeMillis()
        val oldest = connections.values.minOfOrNull { now - it.connectedAt }
        
        return Stats(
            activeConnections = activeCount.get(),
            totalConnectionsServed = connectionIdCounter.get(),
            oldestConnectionAgeMs = oldest
        )
    }
    
    private fun broadcast(message: OtpMessage) {
        val sseEvent = message.toSseEvent()
        val deadConnections = mutableListOf<String>()
        
        connections.forEach { (id, connection) ->
            try {
                connection.stream.write(sseEvent)
                connection.lastActivityAt = System.currentTimeMillis()
                Logger.v(TAG, "Sent OTP to $id")
            } catch (e: Exception) {
                Logger.d(TAG, "Failed to send to $id: ${e.message}")
                deadConnections.add(id)
            }
        }
        
        deadConnections.forEach { removeConnection(it) }
        
        if (connections.isNotEmpty()) {
            Logger.d(TAG, "Broadcast OTP to ${connections.size} clients")
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
                    try {
                        connection.stream.write(heartbeat)
                        connection.lastActivityAt = System.currentTimeMillis()
                    } catch (e: Exception) {
                        Logger.d(TAG, "Heartbeat failed for $id: ${e.message}")
                        deadConnections.add(id)
                    }
                }
                
                deadConnections.forEach { removeConnection(it) }
                
                if (connections.isNotEmpty()) {
                    Logger.v(TAG, "Heartbeat sent to ${connections.size} connections")
                }
            }
        }
    }
    
    fun shutdown() {
        Logger.i(TAG, "Shutting down SSE manager")
        scope.cancel()
        connections.values.forEach { it.stream.closeStream() }
        connections.clear()
        activeCount.set(0)
    }
}