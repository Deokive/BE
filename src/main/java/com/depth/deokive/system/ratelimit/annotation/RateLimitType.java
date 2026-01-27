package com.depth.deokive.system.ratelimit.annotation;

/**
 * Rate Limit 적용 시 식별 방식
 */
public enum RateLimitType {
    USER,   // 인증된 사용자의 userId 기준
    IP,     // 클라이언트 IP 기준(Trusted Proxy 조건에서만 XFF 신뢰)
    EMAIL,  // 인증/인가 API에서 email 기반(PII 보호 위해 HMAC 해시 사용)

    /**
     * 인증 여부에 따라 자동 선택
     * - 인증된 사용자: userId
     * - 비인증: IP
     *
     * ※ 최적 정책: 인증 API에는 AUTO 사용 금지(IP/EMAIL을 명시적으로 붙인다)
     */
    AUTO
}
