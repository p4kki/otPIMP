package com.nesto.otpimp.domain.usecase

import com.nesto.otpimp.domain.parser.SmsParser

/**
 * Use case for getting the list of employees.
 */
class GetEmployeesUseCase(
    private val smsParser: SmsParser
) {
    operator fun invoke(): List<String> = smsParser.getEmployeeNames()
}