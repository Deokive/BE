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
public class TikTokOEmbedProvider implements MetadataProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String TIKTOK_HOST = "tiktok.com";

    // 틱톡 oEmbed 엔드포인트
    private static final String OEMBED_ENDPOINT = "https://www.tiktok.com/oembed";

    @Override
    public boolean supports(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        // tiktok.com이 포함되어 있으면 지원 (www.tiktok.com, vm.tiktok.com, vt.tiktok.com 등)
        return lowerUrl.contains(TIKTOK_HOST);
    }

    @Override
    public OgMetadata extract(String url) {
        try {
            // 1. 요청 URL 생성
            String requestUrl = UriComponentsBuilder.fromUriString(OEMBED_ENDPOINT)
                    .queryParam("url", url)
                    .build()
                    .toUriString();

            // 2. RestClient로 API 호출
            String response = restClient.get()
                    .uri(requestUrl)
                    .retrieve()
                    .body(String.class);

            // 3. JSON 파싱
            JsonNode root = objectMapper.readTree(response);

            String title = root.path("title").asText();
            String thumbnailUrl = root.path("thumbnail_url").asText();
            String authorName = root.path("author_name").asText();

            // 제목이 비어있으면 작성자 이름으로 대체 (틱톡은 제목 없는 경우도 있음)
            if (title == null || title.isBlank()) {
                title = authorName + "님의 틱톡 영상";
            }

            log.info("✅ [TikTok API] Success: {}", title);

            return OgMetadata.builder()
                    .title(title)
                    .imageUrl(thumbnailUrl)
                    .build();

        } catch (Exception e) {
            log.error("❌ [TikTok API] Failed: {}", e.getMessage());
            // 실패 시 GenericProvider(Jsoup)이나 에러 처리로 넘김
            throw new RuntimeException("TikTok oEmbed failed", e);
        }
    }
}