package com.nesto.otpimp.network.handlers

import com.nesto.otpimp.network.SseConnectionManager
import com.nesto.otpimp.util.Constants
import com.nesto.otpimp.util.Logger
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status

class StreamHandler(
    private val sseManager: SseConnectionManager
) {
    companion object {
        private const val TAG = "StreamHandler"
    }
    
    fun handle(): NanoHTTPD.Response {
        val result = sseManager.createConnection()
        
        if (result == null) {
            Logger.w(TAG, "Connection rejected - max connections reached")
            return NanoHTTPD.newFixedLengthResponse(
                Status.SERVICE_UNAVAILABLE,
                Constants.MimeTypes.JSON,
                """{"error": "Max connections reached", "retry_after": 30}"""
            ).apply {
                addHeader("Retry-After", "30")
            }
        }
        
        val (connectionId, inputStream) = result
        
        Logger.i(TAG, "SSE stream started: $connectionId")
        
        return NanoHTTPD.newChunkedResponse(
            Status.OK,
            Constants.MimeTypes.EVENT_STREAM,
            inputStream  // SseOutputStream IS-A InputStream
        ).apply {
            addHeader(Constants.Headers.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            addHeader(Constants.Headers.CONNECTION, "keep-alive")
            addHeader(Constants.Headers.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            addHeader("X-Accel-Buffering", "no")  // Prevents proxy buffering
            addHeader("X-Connection-Id", connectionId)
        }
    }
}