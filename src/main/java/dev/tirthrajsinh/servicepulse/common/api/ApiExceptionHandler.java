package dev.tirthrajsinh.servicepulse.common.api;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.tirthrajsinh.servicepulse.incident.domain.InvalidIncidentTransitionException;
import dev.tirthrajsinh.servicepulse.identity.InvalidCredentialsException;
import dev.tirthrajsinh.servicepulse.identity.InvalidRefreshTokenException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Resource not found",
            exception.getMessage(),
            "resource-not-found"
        );
    }

    @ExceptionHandler(InvalidIncidentTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidIncidentTransitionException exception) {
        return problem(
            HttpStatus.CONFLICT,
            "Invalid incident transition",
            exception.getMessage(),
            "invalid-incident-transition"
        );
    }

    @ExceptionHandler(ResourceConflictException.class)
    ProblemDetail handleConflict(ResourceConflictException exception) {
        return problem(
            HttpStatus.CONFLICT,
            "Resource conflict",
            exception.getMessage(),
            "resource-conflict"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(
            HttpStatus.BAD_REQUEST,
            "Request validation failed",
            "One or more request fields are invalid.",
            "request-validation"
        );
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
            errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        ProblemDetail detail = problem(
            HttpStatus.BAD_REQUEST,
            "Request validation failed",
            "One or more request parameters are invalid.",
            "request-validation"
        );
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            int lastSeparator = path.lastIndexOf('.');
            String field = lastSeparator >= 0 ? path.substring(lastSeparator + 1) : path;
            errors.putIfAbsent(field, violation.getMessage());
        });
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException exception) {
        ProblemDetail detail = problem(
            HttpStatus.BAD_REQUEST,
            "Request validation failed",
            "One or more request parameters are invalid.",
            "request-validation"
        );
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String field = parameterName == null ? "request" : parameterName;
            result.getResolvableErrors().forEach(error ->
                errors.putIfAbsent(field, error.getDefaultMessage())
            );
        });
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    ProblemDetail handleAuthenticationFailure(RuntimeException exception) {
        return problem(
            HttpStatus.UNAUTHORIZED,
            "Authentication failed",
            exception.getMessage(),
            "authentication-failed"
        );
    }

    private ProblemDetail problem(
        HttpStatus status,
        String title,
        String detail,
        String type
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://servicepulse.local/problems/" + type));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
