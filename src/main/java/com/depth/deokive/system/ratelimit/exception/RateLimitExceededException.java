package com.depth.deokive.system.ratelimit.exception;

import lombok.Getter;

/**
 * Rate Limit 초과 시 발생하는 예외
 *
 * HTTP 429 Too Many Requests 응답으로 변환됩니다.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    /**
     * 재시도까지 대기해야 하는 시간 (초)
     */
    private final long retryAfterSeconds;

    /**
     * 남은 토큰 수 (항상 0)
     */
    private final long remainingTokens;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(String.format("🚫Rate limit exceeded. Please retry after %d seconds.", retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
        this.remainingTokens = 0;
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.remainingTokens = 0;
    }
}
