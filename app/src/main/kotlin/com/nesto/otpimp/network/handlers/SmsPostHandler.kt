package com.nesto.otpimp.network.handlers

import com.nesto.otpimp.domain.usecase.ProcessIncomingSmsUseCase
import com.nesto.otpimp.util.Constants
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class SmsPostHandler(
    private val processIncomingSmsUseCase: ProcessIncomingSmsUseCase
) {
    
    fun handle(session: IHTTPSession): NanoHTTPD.Response {
        return try {
            val bodyMap = mutableMapOf<String, String>()
            session.parseBody(bodyMap)
            
            val raw = bodyMap["postData"]
                ?: return errorResponse(Status.BAD_REQUEST, "Empty body")
            
            val json = JSONObject(raw)
            val sender = json.optString("sender", "unknown")
            val body = json.optString("body", "").trim()
            
            if (body.isEmpty()) {
                return errorResponse(Status.BAD_REQUEST, "Missing 'body' field")
            }
            
            val result = runBlocking(Dispatchers.IO){
                processIncomingSmsUseCase(sender, body)
            }
            
            val responseJson = when (result) {
                is com.nesto.otpimp.data.model.Result.Success -> {
                    JSONObject().apply {
                        put("status", "ok")
                        put("employee_name", result.data.employeeName)
                        put("otp_code", result.data.otpCode)
                    }
                }
                is com.nesto.otpimp.data.model.Result.Error -> {
                    return errorResponse(Status.INTERNAL_ERROR, result.message ?: "Processing failed")
                }
                else -> {
                    return errorResponse(Status.INTERNAL_ERROR, "Unexpected state")
                }
            }
            
            NanoHTTPD.newFixedLengthResponse(
                Status.OK,
                Constants.MimeTypes.JSON,
                responseJson.toString()
            )
            
        } catch (e: Exception) {
            errorResponse(Status.INTERNAL_ERROR, "Parse error: ${e.message}")
        }
    }
    
    private fun errorResponse(status: Status, message: String): NanoHTTPD.Response {
        val json = JSONObject().put("error", message).toString()
        return NanoHTTPD.newFixedLengthResponse(status, Constants.MimeTypes.JSON, json)
    }
}
