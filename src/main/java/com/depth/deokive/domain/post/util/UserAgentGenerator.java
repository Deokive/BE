package com.depth.deokive.domain.post.util;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**
 * 실제 브라우저 User Agent를 생성하는 유틸리티
 * 
 * [사용 목적]
 * - OG 메타데이터 추출 시 봇으로 인식되지 않도록 실제 브라우저처럼 보이게 함
 * - 주기적으로 다른 User Agent를 사용하여 패턴 감지 방지
 * 
 * [전략]
 * - 랜덤 선택: 매 요청마다 랜덤하게 선택
 * - 시간 기반 로테이션: 시간대별로 다른 User Agent 사용 (추후 확장 가능)
 */
@Slf4j
public class UserAgentGenerator {
    
    private static final Random random = new SecureRandom();
    
    /**
     * 실제 사용되는 최신 브라우저 User Agent 목록
     * - Chrome, Firefox, Safari, Edge 등 다양한 브라우저와 OS 조합
     * - 주기적으로 업데이트하여 최신 버전 유지 권장
     */
    private static final List<String> USER_AGENTS = List.of(
        // Chrome on Windows
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        
        // Chrome on macOS
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        
        // Chrome on Linux
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        
        // Firefox on Windows
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0",
        
        // Firefox on macOS
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:132.0) Gecko/20100101 Firefox/132.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:131.0) Gecko/20100101 Firefox/131.0",
        
        // Safari on macOS
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15",
        
        // Edge on Windows
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
        
        // Edge on macOS
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
        
        // Chrome on Android (모바일)
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
        
        // Safari on iOS
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"
    );
    
    /**
     * 랜덤하게 User Agent를 선택하여 반환
     * 
     * @return 랜덤하게 선택된 User Agent 문자열
     */
    public static String getRandom() {
        String userAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
        log.debug("[UserAgent] Selected: {}", userAgent);
        return userAgent;
    }
    
    /**
     * 시간 기반으로 User Agent를 선택 (시간대별로 다른 User Agent 사용)
     * 
     * [로직]
     * - 현재 시간(초)을 인덱스로 사용하여 순환 선택
     * - 같은 시간대에는 같은 User Agent 사용 (패턴 감지 방지)
     * 
     * @return 시간 기반으로 선택된 User Agent 문자열
     */
    public static String getByTime() {
        int index = (int) (System.currentTimeMillis() / 1000) % USER_AGENTS.size();
        String userAgent = USER_AGENTS.get(index);
        log.debug("[UserAgent] Selected by time (index={}): {}", index, userAgent);
        return userAgent;
    }
    
    /**
     * 시간 기반 로테이션 (더 긴 주기)
     * 
     * @param rotationPeriodSeconds 로테이션 주기 (초 단위)
     * @return 주기적으로 변경되는 User Agent 문자열
     */
    public static String getByRotation(long rotationPeriodSeconds) {
        int index = (int) (System.currentTimeMillis() / (rotationPeriodSeconds * 1000)) % USER_AGENTS.size();
        String userAgent = USER_AGENTS.get(index);
        log.debug("[UserAgent] Selected by rotation (period={}s, index={}): {}", 
                rotationPeriodSeconds, index, userAgent);
        return userAgent;
    }
    
    /**
     * User Agent 목록의 크기 반환
     * 
     * @return User Agent 목록 크기
     */
    public static int getPoolSize() {
        return USER_AGENTS.size();
    }
}
