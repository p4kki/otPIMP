package com.nesto.otpimp.service

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Holds runtime state for the OTP service.
 * Thread-safe for concurrent access.
 */
class ServiceState {
    val startedAt: Long = System.currentTimeMillis()
    val messagesReceived = AtomicInteger(0)
    val messagesBroadcast = AtomicInteger(0)
    val lastMessageAt = AtomicLong(0)
    
    @Volatile
    var isRunning: Boolean = false
        private set
    
    fun setRunning(running: Boolean) {
        isRunning = running
    }
    
    fun recordMessageReceived() {
        messagesReceived.incrementAndGet()
        lastMessageAt.set(System.currentTimeMillis())
    }
    
    fun recordMessageBroadcast() {
        messagesBroadcast.incrementAndGet()
    }
    
    fun getUptime(): Long = System.currentTimeMillis() - startedAt
}