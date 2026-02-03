package com.depth.deokive.domain.post.util;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.SocketTimeoutException;

@Slf4j
public class OpenGraphExtractor {
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_BODY_SIZE = 5 * 1024 * 1024; // 5MB (OOM 방지)

    public static OgMetadata extract(String url) throws IOException {
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent(UserAgentGenerator.getRandom())  // 실제 브라우저 User Agent 랜덤 선택
                    .maxBodySize(MAX_BODY_SIZE)      // OOM 방지
                    .followRedirects(true)            // 리다이렉트 허용 (Jsoup 기본 20회 제한)
                    .ignoreHttpErrors(false)          // 4xx/5xx 에러 시 예외 발생
                    .get();

            // [임시 DEBUG] EC2에서 실제로 받은 <head> 내용 확인용 — 진단 후 제거
            String headHtml = doc.head().html();
            log.warn("[OG DEBUG] url={} | head-length={} | head={}",
                    url, headHtml.length(), headHtml.substring(0, Math.min(headHtml.length(), 2000)));

            String title = extractOgTag(doc, "og:title");
            if (title == null || title.isBlank()) {
                title = doc.title();
            }
            // DB 컬럼 제한(VARCHAR(255)) 준수: 252자까지 자르고 "..." 붙여서 총 255자
            title = truncateTitle(title);

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
            throw e;
        } catch (IOException e) {
            log.warn("Failed to extract OG metadata from URL: {}", url, e);
            throw e;
        }
    }

    private static String extractOgTag(Document doc, String property) {
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
    private static String truncateTitle(String title) {
        if (title == null) {
            return null;
        }
        if (title.length() <= 252) {
            return title;
        }
        return title.substring(0, 252) + "...";
    }

    @Data
    @Builder
    public static class OgMetadata {
        private String title;
        private String imageUrl;
    }
}
