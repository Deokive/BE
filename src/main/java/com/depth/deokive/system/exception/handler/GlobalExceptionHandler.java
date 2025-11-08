package com.depth.deokive.system.exception.handler;

import com.depth.deokive.system.exception.dto.ErrorResponse;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.jwt.exception.*;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RestException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(RestException e) {
        return createErrorResponse(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, "ILLEGAL ARGUMENT EXCEPTION", e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        String errorMessage = e.getMessage();
        // 꼭 Entity Unique Constraints name 과 일치 하는지, 혹은 따로 명시 하지는 않았는 지 반드시 확인할 것

        log.info("🔴 DataIntegrityViolationException: {}", errorMessage);
        if (errorMessage != null && errorMessage.contains("USER_EMAIL")) {
            return createErrorResponse(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
        } else if (errorMessage != null && errorMessage.contains("USER_USERNAME")) {
            return createErrorResponse(ErrorCode.USER_USERNAME_ALREADY_EXISTS);
        } else {
            return createErrorResponse(ErrorCode.GLOBAL_ALREADY_RESOURCE);
        }
    }

    @ExceptionHandler(JwtMissingException.class)
    public ResponseEntity<ErrorResponse> handleJwtMissingException() {
        return createErrorResponse(ErrorCode.JWT_MISSING);
    }

    @ExceptionHandler(JwtExpiredException.class)
    public ResponseEntity<ErrorResponse> handleJwtExpiredException() {
        return createErrorResponse(ErrorCode.JWT_EXPIRED);
    }

    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleJwtAuthenticationException() {
        return createErrorResponse(ErrorCode.JWT_AUTHENTICATION_FAILED);
    }

    @ExceptionHandler(JwtInvalidException.class)
    public ResponseEntity<ErrorResponse> handleJwtInvalidException() {
        return createErrorResponse(ErrorCode.JWT_INVALID);
    }

    @ExceptionHandler(JwtParseException.class)
    public ResponseEntity<ErrorResponse> handleJwtParseException() {
        return createErrorResponse(ErrorCode.JWT_FAILED_PARSING);
    }

    @ExceptionHandler(JwtBlacklistException.class)
    public ResponseEntity<ErrorResponse> handleJwtBlacklistException() {
        return createErrorResponse(ErrorCode.JWT_BLACKLIST);
    }

    @ExceptionHandler(JwtMalformedException.class)
    public ResponseEntity<ErrorResponse> handleJwtMalformedException() {
        return createErrorResponse(ErrorCode.JWT_MALFORMED);
    }

    @ExceptionHandler(HttpMessageConversionException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageConversionException(){
        return createErrorResponse(ErrorCode.GLOBAL_BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException() {
        return createErrorResponse(ErrorCode.GLOBAL_METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        String param = e.getParameterName();
        String message = "필수 요청 파라미터가 누락되었습니다: " + param;
        return createErrorResponse(HttpStatus.BAD_REQUEST, "GLOBAL_INVALID_PARAMETER", message);
    }

    // ★ @RequestParam/@PathVariable 검증 실패 처리
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse("요청 파라미터가 올바르지 않습니다.");
        return createErrorResponse(HttpStatus.BAD_REQUEST, "GLOBAL_INVALID_PARAMETER", msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {
        var messages = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " : " + err.getDefaultMessage())
                .toList();
        return createErrorResponse(ErrorCode.GLOBAL_BAD_REQUEST, String.join(", ", messages));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[INTERNAL ERROR] {}", e.getMessage(), e);
        return createErrorResponse(ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(OAuth2AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleOAuth2(OAuth2AuthenticationException e) {
        return createErrorResponse(ErrorCode.OAUTH_BAD_REQUEST);
    }

    private ResponseEntity<ErrorResponse> createErrorResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, error, message));
    }

    private ResponseEntity<ErrorResponse> createErrorResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getMessage()));
    }

    private ResponseEntity<ErrorResponse> createErrorResponse(ErrorCode errorCode, String customMessage) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, customMessage));
    }
}
