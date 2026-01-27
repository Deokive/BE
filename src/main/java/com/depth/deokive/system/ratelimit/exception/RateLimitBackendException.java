package com.depth.deokive.system.ratelimit.exception;

public class RateLimitBackendException extends RuntimeException {

    public RateLimitBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
