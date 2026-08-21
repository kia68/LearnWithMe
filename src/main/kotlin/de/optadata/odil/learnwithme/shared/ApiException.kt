package de.optadata.odil.learnwithme.shared

import org.springframework.http.HttpStatus

/**
 * Basis für fachliche Fehler, die als `application/problem+json` (RFC 9457) beantwortet werden.
 * Liegt bewusst im Wurzelpaket von `shared`, nicht in `shared.web`: Spring Modulith behandelt
 * nur das Wurzelpaket eines Moduls als öffentliche API (Named Interface) — jedes andere Modul,
 * das diese Typen wirft/fängt, würde sonst gegen die Modulgrenzen aus §6.3 verstoßen
 * (siehe `ModularityTest`).
 */
open class ApiException(
    val status: HttpStatus,
    val title: String,
    message: String,
) : RuntimeException(message)

class NotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, "Not Found", message)

class ConflictException(message: String) : ApiException(HttpStatus.CONFLICT, "Conflict", message)

class UnauthorizedException(message: String) : ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", message)

class ForbiddenException(message: String) : ApiException(HttpStatus.FORBIDDEN, "Forbidden", message)

class QuotaExceededException(message: String) : ApiException(HttpStatus.PAYMENT_REQUIRED, "Quota Exceeded", message)

class BadGatewayException(message: String) : ApiException(HttpStatus.BAD_GATEWAY, "Upstream Provider Error", message)
