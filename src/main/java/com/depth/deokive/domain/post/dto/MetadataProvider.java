package com.depth.deokive.domain.post.dto;

public interface MetadataProvider {
    boolean supports(String url); // 해당 전략이 지원하는 도메인인지 확인
    OgMetadata extract(String url); // 메타데이터 추출
}
