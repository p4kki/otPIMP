package com.nesto.otpimp.network.handlers

import android.content.Context
import com.nesto.otpimp.util.Constants
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.BufferedReader
import java.io.InputStreamReader

class RootHandler(private val context: Context) {
    
    fun handle(): NanoHTTPD.Response {
        val html = try {
            context.assets.open("index.html").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            return NanoHTTPD.newFixedLengthResponse(
                Status.INTERNAL_ERROR,
                "text/plain",
                "Failed to load index.html: ${e.message}"
            )
        }
        
        return NanoHTTPD.newFixedLengthResponse(
            Status.OK,
            Constants.MimeTypes.HTML,
            html
        )
    }
}
