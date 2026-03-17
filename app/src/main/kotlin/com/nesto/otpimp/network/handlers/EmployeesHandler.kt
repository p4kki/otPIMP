package com.nesto.otpimp.network.handlers

import com.nesto.otpimp.domain.usecase.GetEmployeesUseCase
import com.nesto.otpimp.util.Constants
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONArray
import org.json.JSONObject

class EmployeesHandler(
    private val getEmployeesUseCase: GetEmployeesUseCase
) {
    
    fun handle(): NanoHTTPD.Response {
        val employees = getEmployeesUseCase()
        
        val response = JSONObject().apply {
            put("employees", JSONArray(employees))
            put("count", employees.size)
        }
        
        return NanoHTTPD.newFixedLengthResponse(
            Status.OK,
            Constants.MimeTypes.JSON,
            response.toString()
        ).apply {
            addHeader(Constants.Headers.CACHE_CONTROL, "max-age=60")
        }
    }
}