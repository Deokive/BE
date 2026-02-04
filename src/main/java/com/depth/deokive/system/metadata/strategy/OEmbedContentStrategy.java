package com.depth.deokive.system.metadata.strategy;

import com.depth.deokive.system.metadata.dto.OEmbedDto;

public interface OEmbedContentStrategy {
    boolean supports(String url); // 해당 URL 패턴을 지원하는지 확인 (Regex 매칭)
    OEmbedDto createOEmbed(String url); // 실제 데이터 조회 및 DTO 변환
}