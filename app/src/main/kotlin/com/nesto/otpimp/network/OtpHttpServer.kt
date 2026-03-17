package com.nesto.otpimp.network

import com.nesto.otpimp.data.repository.OtpRepository
import com.nesto.otpimp.domain.usecase.GetEmployeesUseCase
import com.nesto.otpimp.network.handlers.*
import com.nesto.otpimp.service.ServiceState
import com.nesto.otpimp.util.Constants
import com.nesto.otpimp.util.Logger
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class OtpHttpServer(
    private val port: Int = Constants.SERVER_PORT,
    private val otpRepository: OtpRepository,
    private val getEmployeesUseCase: GetEmployeesUseCase,
    private val serviceState: ServiceState
) : NanoHTTPD(port) {
    
    companion object {
        private const val TAG = "OtpHttpServer"
    }
    
    private val sseManager = SseConnectionManager(otpRepository.otpStream)
    
    private val rootHandler = RootHandler()
    private val streamHandler = StreamHandler(sseManager)
    private val healthHandler = HealthHandler(sseManager, serviceState)
    private val employeesHandler = EmployeesHandler(getEmployeesUseCase)
    
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
            when {
                method == Method.OPTIONS -> handleCors()
                uri == Constants.Endpoints.ROOT -> rootHandler.handle()
                uri == Constants.Endpoints.STREAM -> streamHandler.handle()
                uri == Constants.Endpoints.HEALTH -> healthHandler.handle()
                uri == Constants.Endpoints.EMPLOYEES -> employeesHandler.handle()
                else -> handleNotFound(uri)
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
        val response = """{"error": "Not found", "path": "$uri"}"""
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            Constants.MimeTypes.JSON,
            response
        )
    }
    
    private fun handleError(e: Exception): Response {
        val response = """{"error": "Internal server error", "message": "${e.message}"}"""
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            Constants.MimeTypes.JSON,
            response
        )
    }
    
    private fun addCorsHeaders(response: Response) {
        response.addHeader(Constants.Headers.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        response.addHeader(Constants.Headers.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS")
        response.addHeader(Constants.Headers.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Cache-Control")
    }
}