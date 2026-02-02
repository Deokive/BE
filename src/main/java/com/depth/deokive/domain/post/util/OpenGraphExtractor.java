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
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_BODY_SIZE = 5 * 1024 * 1024; // 5MB (OOM 방지)

    public static OgMetadata extract(String url) throws IOException {
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (compatible; DeokiveBot/1.0)")
                    .maxBodySize(MAX_BODY_SIZE)      // OOM 방지
                    .followRedirects(true)            // 리다이렉트 허용 (Jsoup 기본 20회 제한)
                    .ignoreHttpErrors(false)          // 4xx/5xx 에러 시 예외 발생
                    .get();

            String title = extractOgTag(doc, "og:title");
            if (title == null || title.isBlank()) {
                title = doc.title();
            }

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

    @Data
    @Builder
    public static class OgMetadata {
        private String title;
        private String imageUrl;
    }
}
