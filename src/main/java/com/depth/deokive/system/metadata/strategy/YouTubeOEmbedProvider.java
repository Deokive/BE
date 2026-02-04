package com.depth.deokive.system.metadata.strategy;

import com.depth.deokive.domain.post.dto.MetadataProvider;
import com.depth.deokive.domain.post.dto.OgMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class YouTubeOEmbedProvider implements MetadataProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // 지원하는 도메인 패턴
    private static final String YOUTUBE_HOST = "youtube.com";
    private static final String YOUTU_BE_HOST = "youtu.be";
    private static final String OEMBED_ENDPOINT = "https://www.youtube.com/oembed";

    @Override
    public boolean supports(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(YOUTUBE_HOST) || lowerUrl.contains(YOUTU_BE_HOST);
    }

    @Override
    public OgMetadata extract(String url) {
        try {
            // 1. 요청 URL 생성 (Query Parameter 안전하게 처리)
            String requestUrl = UriComponentsBuilder.fromUriString(OEMBED_ENDPOINT)
                    .queryParam("url", url)
                    .queryParam("format", "json")
                    .build()
                    .toUriString();

            // 2. RestClient로 API 호출 (Fluent API)
            String response = restClient.get()
                    .uri(requestUrl)
                    .retrieve()
                    .body(String.class);

            // 3. 응답 파싱
            JsonNode root = objectMapper.readTree(response);
            String title = root.path("title").asText();
            String thumbnailUrl = root.path("thumbnail_url").asText();

            log.info("✅ [YouTube API] Success: {}", title);

            return OgMetadata.builder()
                    .title(title)
                    .imageUrl(thumbnailUrl)
                    .build();

        } catch (Exception e) {
            // 4xx, 5xx 에러나 파싱 에러 발생 시 로그를 남기고 예외를 던져 Consumer가 처리하게 함
            log.error("❌ [YouTube API] Failed: {}", e.getMessage());
            throw new RuntimeException("YouTube oEmbed failed", e);
        }
    }
}