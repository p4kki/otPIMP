package com.nesto.otpimp.util

import com.nesto.otpimp.BuildConfig

object Constants {
    const val SERVER_PORT = BuildConfig.SERVER_PORT
    const val SSE_HEARTBEAT_INTERVAL_MS = BuildConfig.SSE_HEARTBEAT_INTERVAL_MS
    const val MAX_SSE_CONNECTIONS = BuildConfig.MAX_SSE_CONNECTIONS
    
    const val DATABASE_NAME = "otp_database"
    const val DATABASE_VERSION = 1
    
    const val NOTIFICATION_CHANNEL_ID = "otp_server_channel"
    const val NOTIFICATION_ID = 1001
    
    const val SSE_RETRY_MS = 3000
    
    object Endpoints {
        const val ROOT = "/"
        const val STREAM = "/stream"
        const val HEALTH = "/health"
        const val EMPLOYEES = "/employees"
        const val SMS = "/sms"
        const val HISTORY = "/history"
    }
    
    object Headers {
        const val CONTENT_TYPE = "Content-Type"
        const val CACHE_CONTROL = "Cache-Control"
        const val CONNECTION = "Connection"
        const val ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin"
        const val ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods"
        const val ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers"
    }
    
    object MimeTypes {
        const val JSON = "application/json"
        const val HTML = "text/html"
        const val EVENT_STREAM = "text/event-stream"
    }
}