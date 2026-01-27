package com.depth.deokive.system.ratelimit.aspect;

import com.depth.deokive.system.ratelimit.annotation.RateLimit;
import com.depth.deokive.system.ratelimit.config.RateLimitProperties;
import com.depth.deokive.system.ratelimit.exception.RateLimitBackendException;
import com.depth.deokive.system.ratelimit.exception.RateLimitExceededException;
import com.depth.deokive.system.ratelimit.resolver.RateLimitKeyResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final LettuceBasedProxyManager<String> proxyManagerFailOpen;
    private final LettuceBasedProxyManager<String> proxyManagerFailClosed;

    private final RateLimitKeyResolver keyResolver;
    private final RateLimitProperties props;
    private final MeterRegistry meterRegistry;

    /**
     * 정책 캐시 키: "method + (type/capacity/refillTokens/refillPeriod)" 조합
     * - 동일 메서드라도 RateLimit 어노테이션이 여러 개(Repeatable)일 수 있어 Method만으로는 오염 가능
     */
    private final ConcurrentMap<String, BucketConfiguration> configCache = new ConcurrentHashMap<>();
    private final AtomicLong lastBackendErrorLogAtMs = new AtomicLong(0);

    @Around("@annotation(com.depth.deokive.system.ratelimit.annotation.RateLimit) || @annotation(com.depth.deokive.system.ratelimit.annotation.RateLimits)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method declaredMethod = signature.getMethod();

        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        Method mostSpecificMethod = AopUtils.getMostSpecificMethod(declaredMethod, targetClass);

        RateLimit[] limits = mostSpecificMethod.getAnnotationsByType(RateLimit.class);
        if (limits == null || limits.length == 0) {
            return joinPoint.proceed();
        }

        // 고정 우선순위: IP -> EMAIL -> USER -> AUTO
        Arrays.sort(limits, Comparator.comparingInt(this::typePriority));

        for (RateLimit rateLimit : limits) {
            String keyPrefix = resolveKeyPrefix(rateLimit, mostSpecificMethod);
            String key = keyResolver.resolveKey(keyPrefix, rateLimit.type(), joinPoint);
            enforceRateLimit(key, keyPrefix, rateLimit);
        }

        return joinPoint.proceed();
    }

    private int typePriority(RateLimit rl) {
        return switch (rl.type()) {
            case IP -> 0;
            case EMAIL -> 1;
            case USER -> 2;
            case AUTO -> 3;
        };
    }

    private void enforceRateLimit(String key, String keyPrefix, RateLimit rateLimit) {
        boolean effectiveFailClosed =
                rateLimit.failClosed() || props.getFailure().getMode() == RateLimitProperties.FailureMode.FAIL_CLOSED;

        LettuceBasedProxyManager<String> proxyManager =
                effectiveFailClosed ? proxyManagerFailClosed : proxyManagerFailOpen;

        try {
            Bucket bucket = resolveBucket(proxyManager, key, rateLimit);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            // Record metrics
            meterRegistry.counter("ratelimit.requests",
                    Tags.of(
                            "endpoint", keyPrefix,
                            "type", rateLimit.type().name(),
                            "blocked", String.valueOf(!probe.isConsumed())
                    )
            ).increment();

            if (!probe.isConsumed()) {
                // Record exceeded metric
                meterRegistry.counter("ratelimit.exceeded",
                        Tags.of("endpoint", keyPrefix, "type", rateLimit.type().name())
                ).increment();

                long nanos = probe.getNanosToWaitForRefill();
                long retryAfterSeconds = (long) Math.ceil(nanos / 1_000_000_000.0);
                retryAfterSeconds = Math.max(1, retryAfterSeconds);

                log.warn("🚫 Rate limit exceeded. key={}, retryAfter={}s", key, retryAfterSeconds);
                throw new RateLimitExceededException(retryAfterSeconds);
            }

        } catch (RateLimitExceededException e) {
            throw e;

        } catch (Exception e) {
            logBackendErrorSampled(effectiveFailClosed, key, e);

            if (effectiveFailClosed) {
                throw new RateLimitBackendException("Rate limit backend error (FAIL_CLOSED)", e);
            }
            // FAIL_OPEN: rate-limit 포기하고 통과
        }
    }

    private Bucket resolveBucket(LettuceBasedProxyManager<String> proxyManager, String key, RateLimit rateLimit) {
        String cacheKey = buildPolicyCacheKey(rateLimit);

        BucketConfiguration configuration =
                configCache.computeIfAbsent(cacheKey, k -> buildBucketConfiguration(rateLimit));

        return proxyManager.builder().build(key, () -> configuration);
    }

    private String buildPolicyCacheKey(RateLimit rateLimit) {
        // keyPrefix는 keyResolver에서 합쳐지므로, 여기서는 정책만 캐싱한다.
        // (동일 정책이면 BucketConfiguration 재사용)
        return rateLimit.type() + "|" +
                rateLimit.capacity() + "|" +
                rateLimit.refillTokens() + "|" +
                rateLimit.refillPeriodSeconds();
    }

    private BucketConfiguration buildBucketConfiguration(RateLimit rateLimit) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rateLimit.capacity())
                        .refillGreedy(
                                rateLimit.refillTokens(),
                                Duration.ofSeconds(rateLimit.refillPeriodSeconds())
                        )
                        .build())
                .build();
    }

    private String resolveKeyPrefix(RateLimit rateLimit, Method method) {
        String keyPrefix = rateLimit.keyPrefix();
        if (keyPrefix != null && !keyPrefix.isBlank()) {
            return keyPrefix;
        }

        // 최적 keyPrefix: HTTP_METHOD:BEST_MATCHING_PATTERN
        HttpServletRequest request = currentRequest();
        if (request != null) {
            Object patternObj = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String pattern = patternObj != null ? patternObj.toString() : null;
            String httpMethod = request.getMethod();

            if (httpMethod != null && pattern != null && !pattern.isBlank()) {
                return httpMethod + ":" + pattern;
            }
        }

        // fallback(예외 상황): 기존 방식 유지
        String className = method.getDeclaringClass().getSimpleName()
                .replace("Controller", "")
                .toLowerCase();
        return className + ":" + method.getName();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private void logBackendErrorSampled(boolean failClosed, String key, Exception e) {
        long now = System.currentTimeMillis();
        long minInterval = props.getLogging().getBackendErrorMinIntervalMs();

        long last = lastBackendErrorLogAtMs.get();
        if (now - last < minInterval) {
            return;
        }
        if (!lastBackendErrorLogAtMs.compareAndSet(last, now)) {
            return;
        }

        if (failClosed) {
            log.error("Rate limit backend error (FAIL_CLOSED). key={}, err={}", key, e.toString());
        } else {
            log.error("Rate limit backend error (FAIL_OPEN). allow request. key={}, err={}", key, e.toString());
        }
    }
}
