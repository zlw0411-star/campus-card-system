package com.campuscard.server.web;

import com.campuscard.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<WebDtos.ErrorJson> business(BusinessException exception) {
        boolean known = switch (exception.code()) {
            case "AUTH_REQUIRED", "LOGIN_FAILED", "FORBIDDEN", "USER_DISABLED", "CSRF_INVALID", "VALIDATION",
                    "NOT_FOUND", "DUPLICATE", "OPERATION_CONFLICT", "INSUFFICIENT_BALANCE", "DATABASE_ERROR" -> true;
            default -> false;
        };
        HttpStatus status = switch (exception.code()) {
            case "AUTH_REQUIRED", "LOGIN_FAILED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN", "USER_DISABLED", "CSRF_INVALID" -> HttpStatus.FORBIDDEN;
            case "VALIDATION" -> HttpStatus.BAD_REQUEST;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DUPLICATE", "OPERATION_CONFLICT" -> HttpStatus.CONFLICT;
            case "INSUFFICIENT_BALANCE" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "DATABASE_ERROR" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        String message = status == HttpStatus.INTERNAL_SERVER_ERROR ? "服务器暂时无法处理请求" : exception.getMessage();
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) LOG.error("Unknown business error code: {}", exception.code(), exception);
        return ResponseEntity.status(status).body(new WebDtos.ErrorJson(known ? exception.code() : "SERVER_ERROR", message));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<WebDtos.ErrorJson> validation(Exception exception) {
        return ResponseEntity.badRequest().body(new WebDtos.ErrorJson("VALIDATION", "请求参数或数据格式无效"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<WebDtos.ErrorJson> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new WebDtos.ErrorJson("VALIDATION", "请求方式无效"));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<WebDtos.ErrorJson> notFound(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new WebDtos.ErrorJson("NOT_FOUND", "请求的资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<WebDtos.ErrorJson> unexpected(Exception exception) {
        LOG.error("Unhandled server error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new WebDtos.ErrorJson("SERVER_ERROR", "服务器暂时无法处理请求"));
    }
}
