package com.nesto.otpimp.network

import android.content.Context
import com.nesto.otpimp.data.repository.OtpRepository
import com.nesto.otpimp.domain.usecase.GetEmployeesUseCase
import com.nesto.otpimp.domain.usecase.ProcessIncomingSmsUseCase
import com.nesto.otpimp.network.handlers.*
import com.nesto.otpimp.service.ServiceState
import com.nesto.otpimp.util.Constants
import com.nesto.otpimp.util.Logger
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import java.io.IOException
import org.json.JSONObject

sealed class Route(val method: Method, val path: String) {
    object Root : Route(Method.GET, "/")
    object Stream : Route(Method.GET, "/stream")
    object Health : Route(Method.GET, "/health")
    object Employees : Route(Method.GET, "/employees")
    object PostSms : Route(Method.POST, "/sms")

    companion object {
        operator fun invoke(method: Method, path: String): Route? = when {
            method == Method.GET && path == "/" -> Root
            method == Method.GET && path == "/stream" -> Stream
            method == Method.GET && path == "/health" -> Health
            method == Method.GET && path == "/employees" -> Employees
            method == Method.POST && path == "/sms" -> PostSms
            else -> null
        }
    }
}

class OtpHttpServer(
    private val context: Context,
    private val port: Int = Constants.SERVER_PORT,
    private val otpRepository: OtpRepository,
    private val getEmployeesUseCase: GetEmployeesUseCase,
    private val serviceState: ServiceState,
    private val processIncomingSmsUseCase: ProcessIncomingSmsUseCase
) : NanoHTTPD(port) {
    
    companion object {
        private const val TAG = "OtpHttpServer"
        private const val SOCKET_READ_TIMEOUT = 0
    }
    
    private val sseManager = SseConnectionManager(otpRepository.otpStream)
    
    private val rootHandler = RootHandler()
    private val streamHandler = StreamHandler(sseManager)
    private val healthHandler = HealthHandler(sseManager, serviceState)
    private val employeesHandler = EmployeesHandler(getEmployeesUseCase)
    private val smsPostHandler = SmsPostHandler(processIncomingSmsUseCase)
    
    override fun start() {
        try {
            super.start(SOCKET_READ_TIMEOUT, false)
            Logger.i(TAG, "HTTP server started on port $port")
        } catch (e: IOException) {
            Logger.e(TAG, "Failed to start HTTP server", e)
            throw e
        }
    }
    
    override fun stop() {
        sseManager.shutdown()
        super.stop()
        Logger.i(TAG, "HTTP server stopped")
    }
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Logger.v(TAG, "${method.name} $uri")
        
        // Add CORS headers to all responses
        return try {
            when (Route(method, uri)) {
                Route.Root -> rootHandler.handle()
                Route.Stream -> streamHandler.handle()
                Route.Health -> healthHandler.handle()
                Route.Employees -> employeesHandler.handle()
                Route.PostSms -> smsPostHandler.handle(session)
                null -> if (method == Method.OPTIONS) handleCors() else handleNotFound(uri)
            }.apply {
                addCorsHeaders(this)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error handling request: $uri", e)
            handleError(e)
        }
    }
    
    private fun handleCors(): Response {
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
            .apply { addCorsHeaders(this) }
    }
    
    private fun handleNotFound(uri: String): Response {
        val json = JSONObject().apply {
            put("error", "Not found")
            put("path", uri)
        }
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            Constants.MimeTypes.JSON,
            json.toString()
        )
        }
    
    private fun handleError(e: Exception): Response {
        val json = JSONObject().apply {
            put("error", "Internal server error")
            put("message", e.message ?: "Unknown error")
        }
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            Constants.MimeTypes.JSON,
            json.toString()
        )
    }
    
    private fun addCorsHeaders(response: Response) {
        response.addHeader(Constants.Headers.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        response.addHeader(Constants.Headers.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST, OPTIONS")
        response.addHeader(Constants.Headers.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Cache-Control")
    }
}