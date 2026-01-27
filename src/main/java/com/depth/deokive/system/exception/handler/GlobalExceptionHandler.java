package com.depth.deokive.system.exception.handler;

import com.depth.deokive.system.exception.dto.ErrorResponse;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.ratelimit.exception.RateLimitExceededException;
import com.depth.deokive.system.security.jwt.exception.*;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.*;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // TODO: Deprecated Exceptions->Deadlock, CannotSerializable...

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds())) // 표준 헤더
                .body(response);
    }

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
        
        if (errorMessage == null) {
            return createErrorResponse(ErrorCode.GLOBAL_BAD_REQUEST);
        }

        // Step 1: 데이터 길이 초과 감지
        if (errorMessage.contains("Data too long") || errorMessage.contains("too long for column")) {
            String columnName = extractColumnNameFromDataTooLong(errorMessage);
            String customMessage = columnName != null 
                ? String.format("%s 필드의 값이 너무 깁니다.", columnName)
                : "데이터 길이가 허용된 한도를 초과했습니다.";
            return createErrorResponse(ErrorCode.DB_DATA_TOO_LONG, customMessage);
        }

        // Step 2: NOT NULL 제약 위반 감지
        if (errorMessage.contains("cannot be null") || 
            (errorMessage.contains("Column") && errorMessage.contains("cannot be null"))) {
            String columnName = extractColumnNameFromNotNull(errorMessage);
            String customMessage = columnName != null
                ? String.format("%s 필드는 필수입니다.", columnName)
                : "필수 필드가 누락되었습니다.";
            return createErrorResponse(ErrorCode.DB_NOT_NULL_VIOLATION, customMessage);
        }

        // Step 3: 외래 키 제약 위반 감지
        if (errorMessage.contains("foreign key constraint") || 
            errorMessage.contains("Cannot add or update a child row") ||
            errorMessage.contains("a foreign key constraint fails")) {
            return createErrorResponse(ErrorCode.DB_FOREIGN_KEY_VIOLATION);
        }

        // Step 4: UNIQUE 제약 위반 감지 (중복)
        if (errorMessage.contains("Duplicate entry") || 
            errorMessage.contains("UNIQUE constraint") ||
            errorMessage.contains("unique constraint")) {
            // 기존 로직: 특정 제약 이름으로 구분
            if (errorMessage.contains("USER_EMAIL")) {
                return createErrorResponse(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
            } else if (errorMessage.contains("USER_USERNAME")) {
                return createErrorResponse(ErrorCode.USER_USERNAME_ALREADY_EXISTS);
            } else {
                return createErrorResponse(ErrorCode.GLOBAL_ALREADY_RESOURCE);
            }
        }

        // Step 5: 기타 제약 위반
        return createErrorResponse(ErrorCode.GLOBAL_BAD_REQUEST, "데이터 무결성 제약 조건을 위반했습니다.");
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        String fieldName = extractFieldName(e);
        String errorMessage;
        
        if (fieldName != null) {
            String causeMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            // errorMessage = String.format("필드 '%s'의 값 형식이 올바르지 않습니다: %s", fieldName, causeMessage);
            errorMessage = String.format("필드 '%s'의 값 형식이 올바르지 않습니다", fieldName);
        } else {
            String causeMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            // errorMessage = "요청 본문의 JSON 형식이 올바르지 않습니다: " + causeMessage;
            errorMessage = "요청 본문의 JSON 형식이 올바르지 않습니다: ";
        }
        
        log.warn("🔴 JSON 파싱 오류: {}", errorMessage);
        return createErrorResponse(HttpStatus.BAD_REQUEST, "JSON_PARSE_ERROR", errorMessage);
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String fieldName = e.getName();
        // 사용자가 보낸 잘못된 값
        String invalidValue = e.getValue() != null ? e.getValue().toString() : "null";

        // 깔끔한 메시지로 변환
        String errorMessage = String.format("%s : 값의 형식이 올바르지 않습니다. (입력값: %s)", fieldName, invalidValue);

        return createErrorResponse(ErrorCode.GLOBAL_BAD_REQUEST, errorMessage);
    }

    // ★ @RequestParam/@PathVariable 검증 실패 처리
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("요청 파라미터가 올바르지 않습니다.");
        return createErrorResponse(HttpStatus.BAD_REQUEST, "GLOBAL_INVALID_PARAMETER", msg);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleValidationException(Exception e) {
        BindingResult bindingResult = null;
        if (e instanceof BindException) {
            bindingResult = ((BindException) e).getBindingResult();
        }

        String errorMessage = "잘못된 요청입니다.";
        if (bindingResult != null && bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();

            if (fieldError != null) {
                // 1. 타입 변환 실패인지 확인
                // codes 배열에 "typeMismatch"가 포함되어 있거나, isBindingFailure()가 true인 경우
                if (fieldError.isBindingFailure() || "typeMismatch".equals(fieldError.getCode())) {
                    String invalidValue = fieldError.getRejectedValue() != null ? fieldError.getRejectedValue().toString() : "null";
                    errorMessage = fieldError.getField() + " : 값의 형식이 올바르지 않습니다. (입력값: " + invalidValue + ")";
                }
                // 2. 일반 유효성 검사 실패 (@Min, @Max 등)
                else {
                    errorMessage = fieldError.getField() + " : " + fieldError.getDefaultMessage();
                }
            }
        }

        return createErrorResponse(ErrorCode.GLOBAL_BAD_REQUEST, errorMessage);
    }

    // 정적 리소스를 찾을 수 없을 때 처리
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        String resourcePath = e.getResourcePath();
        
        // RequestMatcherHolder의 permitAll 경로가 아니고 /api/**도 아니면 DEBUG 레벨로 처리
        // (SecurityConfig에서 denyAll()로 차단되므로 정상적인 요청이 아님)
        if (!resourcePath.startsWith("/api/")) {
            log.debug("🔍 Non-API resource not found (blocked by denyAll): {}", resourcePath);
        } else {
            log.warn("⚠️ Resource not found: {}", resourcePath);
        }
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        // 정적 리소스 관련 예외인지 확인 (NoResourceFoundException이 잡히지 않은 경우 대비)
        String message = e.getMessage();
        if (message != null && message.contains("No static resource")) {
            // 예외 메시지에서 경로 추출 시도
            String resourcePath = extractResourcePathFromMessage(message);
            
            // /api/**가 아니면 DEBUG 레벨로 처리 (SecurityConfig에서 denyAll()로 차단됨)
            if (resourcePath != null && !resourcePath.startsWith("/api/")) {
                log.debug("🔍 Non-API resource not found (blocked by denyAll): {}", resourcePath);
            } else {
                log.warn("⚠️ Resource not found: {}", resourcePath != null ? resourcePath : message);
            }
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
        }
        
        log.error("[INTERNAL ERROR] {}", e.getMessage(), e);
        return createErrorResponse(ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(OAuth2AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleOAuth2(OAuth2AuthenticationException e) {
        return createErrorResponse(ErrorCode.OAUTH_BAD_REQUEST);
    }

    // 1. DB 연결 실패
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessResourceFailure(
            DataAccessResourceFailureException e) {
        log.error("🔴 DB 리소스 접근 실패: {}", e.getMessage(), e);
        return createErrorResponse(ErrorCode.DB_CONNECTION_FAILED);
    }

    // 2. 쿼리 타임아웃
    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleQueryTimeout(QueryTimeoutException e) {
        log.error("🔴 쿼리 타임아웃: {}", e.getMessage(), e);
        return createErrorResponse(ErrorCode.DB_QUERY_TIMEOUT);
    }

    // 3. 데드락
    @ExceptionHandler(DeadlockLoserDataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDeadlock(DeadlockLoserDataAccessException e) {
        log.error("🔴 데드락 발생: {}", e.getMessage(), e);
        return createErrorResponse(
                ErrorCode.DB_DEADLOCK,
                "동시 접근으로 인한 충돌이 발생했습니다. 잠시 후 다시 시도해주세요."
        );
    }

    // 4. 낙관적 락 실패
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.error("🔴 낙관적 락 실패: {}", e.getMessage(), e);
        return createErrorResponse(
                ErrorCode.DB_OPTIMISTIC_LOCK_FAILED,
                "데이터가 다른 사용자에 의해 수정되었습니다. 새로고침 후 다시 시도해주세요."
        );
    }

    // 5. 비관적 락 실패
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handlePessimisticLock(PessimisticLockingFailureException e) {
        log.error("🔴 비관적 락 실패: {}", e.getMessage(), e);
        return createErrorResponse(
                ErrorCode.DB_PESSIMISTIC_LOCK_FAILED,
                "리소스가 사용 중입니다. 잠시 후 다시 시도해주세요."
        );
    }

    // 6. 결과 크기 불일치
    @ExceptionHandler(IncorrectResultSizeDataAccessException.class)
    public ResponseEntity<ErrorResponse> handleIncorrectResultSize(IncorrectResultSizeDataAccessException e) {
        log.error("🔴 결과 크기 불일치: {}", e.getMessage(), e);
        return createErrorResponse(
                ErrorCode.DB_INCORRECT_RESULT_SIZE,
                "예상과 다른 결과가 반환되었습니다."
        );
    }

    // 7. 트랜잭션 관련
    @ExceptionHandler(CannotSerializeTransactionException.class)
    public ResponseEntity<ErrorResponse> handleTransactionSerialization(CannotSerializeTransactionException e) {
        log.error("🔴 트랜잭션 직렬화 실패: {}", e.getMessage(), e);
        return createErrorResponse(
                ErrorCode.DB_TRANSACTION_SERIALIZATION_FAILED,
                "트랜잭션 처리 중 오류가 발생했습니다."
        );
    }

    // 8. 기타 DataAccessException (포괄 처리)
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException e) {
        log.error("🔴 데이터 접근 예외: {}", e.getMessage(), e);
        return createErrorResponse(
                ErrorCode.DB_DATA_ACCESS_ERROR,
                "데이터베이스 처리 중 오류가 발생했습니다: " + e.getClass().getSimpleName()
        );
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponse> handleRedisConnectionFailure(RedisConnectionFailureException e) {
        log.error("🔴 Redis 연결 실패: {}", e.getMessage(), e);
        return createErrorResponse(ErrorCode.REDIS_CONNECTION_FAILED);
    }

    @ExceptionHandler(RedisException.class)
    public ResponseEntity<ErrorResponse> handleRedisException(RedisException e) {
        log.error("🔴 Redis 오류: {}", e.getMessage(), e);
        return createErrorResponse(ErrorCode.REDIS_ERROR);
    }

    @ExceptionHandler(RedisCommandExecutionException.class)
    public ResponseEntity<ErrorResponse> handleRedisCommandExecution(
            RedisCommandExecutionException e) {
        log.error("🔴 Redis 명령 실행 실패: {}", e.getMessage(), e);
        return createErrorResponse(ErrorCode.REDIS_COMMAND_FAILED);
    }

    // Redis 타임아웃 예외
    @ExceptionHandler(RedisCommandTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleRedisTimeout(
            RedisCommandTimeoutException e) {
        log.error("🔴 Redis 타임아웃: {}", e.getMessage(), e);
        return createErrorResponse(ErrorCode.REDIS_TIMEOUT);
    }

    // Helper Methods
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

    /**
     * "Data too long for column" 메시지에서 컬럼명 추출
     * 예: "Data too long for column 'content' at row 1" → "content"
     */
    private String extractColumnNameFromDataTooLong(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        
        // "Data too long for column 'content' at row 1" 패턴
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "Data too long for column '([^']+)'", 
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(errorMessage);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // "too long for column 'content'" 패턴 (대체)
        pattern = java.util.regex.Pattern.compile(
            "too long for column '([^']+)'", 
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        matcher = pattern.matcher(errorMessage);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    /**
     * "Column '...' cannot be null" 메시지에서 컬럼명 추출
     * 예: "Column 'title' cannot be null" → "title"
     */
    private String extractColumnNameFromNotNull(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        
        // "Column 'title' cannot be null" 패턴
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "Column '([^']+)' cannot be null", 
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(errorMessage);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    /**
     * HttpMessageNotReadableException에서 필드명 추출
     * Jackson의 JsonMappingException을 통해 필드 경로를 추출합니다.
     */
    private String extractFieldName(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        
        if (cause instanceof JsonMappingException) {
            JsonMappingException jsonMappingException = (JsonMappingException) cause;
            var path = jsonMappingException.getPath();
            
            if (path != null && !path.isEmpty()) {
                JsonMappingException.Reference reference = path.get(path.size() - 1);
                if (reference != null) {
                    String fieldName = reference.getFieldName();
                    if (fieldName != null) {
                        return fieldName;
                    }
                    // 필드명이 없으면 인덱스 정보 반환 (배열인 경우)
                    if (reference.getIndex() >= 0) {
                        return "[" + reference.getIndex() + "]";
                    }
                }
            }
        }
        
        // 대안: 예외 메시지에서 필드명 추출 시도
        String message = e.getMessage();
        if (message != null) {
            // "Cannot deserialize value of type `java.time.LocalDate` from String \"2025/12/21\""
            // 같은 메시지에서 패턴 매칭으로 필드명 찾기
            // Jackson이 때때로 메시지에 필드 경로를 포함시킴
            // 예: "JSON parse error: Cannot deserialize value of type `java.time.LocalDate` from String \"2025/12/21\": Failed to deserialize java.time.LocalDate: (java.time.format.DateTimeParseException) Text '2025/12/21' could not be parsed at index 4"
            // 이 경우에는 JsonMappingException의 path를 사용하는 것이 더 정확함
        }
        
        return null;
    }

    /**
     * 예외 메시지에서 리소스 경로 추출
     * "No static resource /path" 형식에서 경로를 추출
     */
    private String extractResourcePathFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        // "No static resource " 이후의 경로 추출
        String prefix = "No static resource ";
        int index = message.indexOf(prefix);
        if (index >= 0) {
            String path = message.substring(index + prefix.length()).trim();
            // 빈 문자열이나 "."인 경우 null 반환
            if (path.isEmpty() || path.equals(".")) {
                return null;
            }
            return path;
        }
        
        return null;
    }

}
