package com.depth.deokive.system.ratelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API Rate Limit 적용을 위한 어노테이션
 *
 * Token Bucket 알고리즘 기반
 * - capacity: 버킷 최대 토큰
 * - refillTokens: 리필 시 추가 토큰
 * - refillPeriodSeconds: 리필 주기(초)
 *
 * Repeatable:
 * - 인증 API에서 @RateLimit(type=IP) + @RateLimit(type=EMAIL) 형태로 2중 제한 적용 가능
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    int capacity();
    int refillTokens();
    int refillPeriodSeconds() default 60;

    /**
     * 비어있으면 "HTTP_METHOD:BEST_MATCHING_PATTERN" 기반으로 자동 생성한다.
     */
    String keyPrefix() default "";

    /**
     * 식별자 타입
     * - 인증 API 2중 제한: IP + EMAIL
     */
    RateLimitType type() default RateLimitType.AUTO;

    /**
     * true면 limiter backend 장애 시 차단(FAIL_CLOSED)
     * false면 backend 장애 시 통과(FAIL_OPEN)
     */
    boolean failClosed() default false;
}
