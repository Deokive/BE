package com.depth.deokive.domain.post.util.strategy;

import com.depth.deokive.domain.post.dto.MetadataProvider;
import com.depth.deokive.domain.post.dto.OgMetadata;
import com.depth.deokive.domain.post.util.UserAgentGenerator;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;

@Slf4j
@Component
public class GenericJsoupProvider implements MetadataProvider {

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_BODY_SIZE = 5 * 1024 * 1024; // 5MB (OOM 방지)

    @Override
    public boolean supports(String url) {
        return true; // 모든 URL의 Fallback으로 사용
    }

    @Override
    public OgMetadata extract(String url) {
        try {
            // Jsoup 연결 설정 (기존 설정 + 스크래핑 성공률 높이는 헤더 추가)
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent(UserAgentGenerator.getRandom())  // 랜덤 User Agent
                    .maxBodySize(MAX_BODY_SIZE)                 // OOM 방지 설정 유지
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7") // 한국어 우선 설정
                    .referrer("http://google.com")              // 레퍼러 우회
                    .followRedirects(true)                      // 리다이렉트 허용
                    .ignoreHttpErrors(false)                    // 4xx, 5xx 에러 시 예외 발생
                    .get();

            // [DEBUG] EC2 환경 진단용 로그 (기존 로직 유지)
            // 추후 안정화되면 제거 가능
            String headHtml = doc.head().html();
            log.warn("[GenericJsoup] url={} | head-length={} | head={}",
                    url, headHtml.length(), headHtml.substring(0, Math.min(headHtml.length(), 2000)));

            // 1. Title 추출
            String title = extractOgTag(doc, "og:title");
            if (title == null || title.isBlank()) {
                title = doc.title();
            }
            // DB 컬럼 제한(VARCHAR(255)) 준수
            title = truncateTitle(title);

            // 2. Image 추출
            String imageUrl = extractOgTag(doc, "og:image");
            if (imageUrl == null || imageUrl.isBlank()) {
                imageUrl = extractOgTag(doc, "twitter:image");
            }

            return OgMetadata.builder()
                    .title(title)
                    .imageUrl(imageUrl)
                    .build();

        } catch (SocketTimeoutException e) {
            log.warn("Timeout extracting OG metadata from URL: {}", url);
            throw new RuntimeException("OG Extraction Timeout", e);
        } catch (IOException e) {
            log.warn("Failed to extract OG metadata from URL: {}", url, e);
            throw new RuntimeException("OG Extraction Failed", e);
        }
    }

    // 태그 추출 헬퍼 메서드
    private String extractOgTag(Document doc, String property) {
        var element = doc.selectFirst("meta[property=" + property + "]");
        if (element == null) {
            element = doc.selectFirst("meta[name=" + property + "]");
        }
        return element != null ? element.attr("content") : null;
    }

    /**
     * 제목을 252자까지 자르고 "..."을 붙여서 총 255자로 제한
     * DB 컬럼 제한(VARCHAR(255))을 준수하기 위함
     */
    private String truncateTitle(String title) {
        if (title == null) {
            return null;
        }
        if (title.length() <= 252) {
            return title;
        }
        return title.substring(0, 252) + "...";
    }
}