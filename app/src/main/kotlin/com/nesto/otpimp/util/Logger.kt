package com.nesto.otpimp.util

import android.util.Log
import com.nesto.otpimp.BuildConfig
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Centralized logging utility with in-memory log buffer for diagnostics.
 */
object Logger {
    
    private const val MAX_LOG_BUFFER_SIZE = 500
    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        fun formatted(): String {
            val time = dateFormat.format(Date(timestamp))
            val error = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
            return "[$time] ${level.name}/$tag: $message$error"
        }
    }
    
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }
    
    fun v(tag: String, message: String) {
        if (BuildConfig.ENABLE_VERBOSE_LOGGING) {
            log(Level.VERBOSE, tag, message)
            Log.v(tag, message)
        }
    }
    
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG || BuildConfig.ENABLE_VERBOSE_LOGGING) {
            log(Level.DEBUG, tag, message)
            Log.d(tag, message)
        }
    }
    
    fun i(tag: String, message: String) {
        log(Level.INFO, tag, message)
        Log.i(tag, message)
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, throwable)
        logBuffer.add(entry)
        
        // Trim buffer if too large
        while (logBuffer.size > MAX_LOG_BUFFER_SIZE) {
            logBuffer.poll()
        }
    }
    
    fun getRecentLogs(count: Int = 100): List<LogEntry> {
        return logBuffer.toList().takeLast(count)
    }
    
    fun getLogsFormatted(count: Int = 100): String {
        return getRecentLogs(count).joinToString("\n") { it.formatted() }
    }
    
    fun clear() {
        logBuffer.clear()
    }
}