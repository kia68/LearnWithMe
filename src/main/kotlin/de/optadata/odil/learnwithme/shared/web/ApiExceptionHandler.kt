package de.optadata.odil.learnwithme.shared.web

import de.optadata.odil.learnwithme.shared.ApiException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(ex.status, ex.message).apply { title = ex.title }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val detail = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Validierung fehlgeschlagen" }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail).apply {
            title = "Validation Failed"
        }
    }
}
