package com.depth.deokive.system.exception.handler;

import com.depth.deokive.system.exception.dto.ErrorResponse;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.jwt.exception.*;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.*;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    // 정적 리소스를 찾을 수 없을 때 처리 (봇/스캐너 요청 포함)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        String resourcePath = e.getResourcePath();
        
        // 봇/스캐너가 요청하는 일반적인 경로는 DEBUG 레벨로 처리
        if (isCommonBotOrScannerPath(resourcePath)) {
            log.debug("🔍 Bot/scanner requested non-existent resource: {}", resourcePath);
            // 404 응답 반환 (봇/스캐너는 404를 정상적으로 처리함)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
        }
        
        // 일반적인 리소스 요청은 WARN 레벨
        log.warn("⚠️ Resource not found: {}", resourcePath);
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
            
            // 봇/스캐너가 요청하는 일반적인 경로는 DEBUG 레벨로 처리
            if (isCommonBotOrScannerPath(resourcePath)) {
                log.debug("🔍 Bot/scanner requested non-existent resource: {}", resourcePath != null ? resourcePath : message);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
            }
            
            // 일반적인 리소스 요청은 WARN 레벨
            log.warn("⚠️ Resource not found: {}", resourcePath != null ? resourcePath : message);
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

    /**
     * 일반적인 봇/스캐너가 요청하는 경로인지 확인
     * @param path 요청 경로
     * @return 봇/스캐너 경로면 true
     */
    private boolean isCommonBotOrScannerPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        String lowerPath = path.toLowerCase();

        // .well-known 경로 (RFC 8615)
        if (lowerPath.startsWith("/.well-known/")) {
            return true;
        }

        // 확장자 기반 체크
        if (lowerPath.endsWith(".txt") ||
            lowerPath.endsWith("accesspolicy.xml") ||
            (lowerPath.contains("sitemap") && lowerPath.endsWith(".xml"))) {
            return true;
        }

        // 특정 파일명 패턴
        if (lowerPath.equals("/security.txt") ||
            lowerPath.equals("/robots.txt") ||
            lowerPath.equals("/favicon.ico") ||
            (lowerPath.startsWith("/sitemap") && lowerPath.endsWith(".xml"))) {
            return true;
        }

        return false;
    }
}
