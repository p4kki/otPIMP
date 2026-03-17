package com.nesto.otpimp.network.handlers

import com.nesto.otpimp.network.SseConnectionManager
import com.nesto.otpimp.util.Constants
import com.nesto.otpimp.util.Logger
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.PipedInputStream
import java.io.PipedOutputStream

class StreamHandler(
    private val sseManager: SseConnectionManager
) {
    companion object {
        private const val TAG = "StreamHandler"
    }
    
    fun handle(): NanoHTTPD.Response {
        val pipedOutput = PipedOutputStream()
        val pipedInput = PipedInputStream(pipedOutput, 8192)
        
        val connectionId = sseManager.registerConnection(pipedOutput)
        
        if (connectionId == null) {
            Logger.w(TAG, "Connection rejected - max connections reached")
            return NanoHTTPD.newFixedLengthResponse(
                Status.SERVICE_UNAVAILABLE,
                Constants.MimeTypes.JSON,
                """{"error": "Max connections reached", "retry_after": 30}"""
            ).apply {
                addHeader("Retry-After", "30")
            }
        }
        
        Logger.d(TAG, "SSE stream started: $connectionId")
        
        return NanoHTTPD.newChunkedResponse(
            Status.OK,
            Constants.MimeTypes.EVENT_STREAM,
            pipedInput
        ).apply {
            addHeader(Constants.Headers.CACHE_CONTROL, "no-cache")
            addHeader(Constants.Headers.CONNECTION, "keep-alive")
            addHeader(Constants.Headers.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            addHeader("X-Connection-Id", connectionId)
        }
    }
}