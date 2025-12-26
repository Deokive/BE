package com.depth.deokive.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class FileUrlUtils {
    private static String cdnBaseUrl;

    @Value("${cdn.base-url:#{null}}")
    public void setCdnBaseUrl(String cdnBaseUrl) {
        FileUrlUtils.cdnBaseUrl =
                cdnBaseUrl.endsWith("/") ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1) : cdnBaseUrl;
    }

    public static String buildCdnUrl(String s3ObjectKey) {
        if (s3ObjectKey == null || s3ObjectKey.isBlank()) return null;
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "CDN base URL이 설정되지 않았습니다. " + "보안을 위해 CDN 설정이 필수입니다. "
            );
        }

        // 경로 정리 (앞의 '/' 제거)
        String cleanPath = s3ObjectKey.startsWith("/")
                ? s3ObjectKey.substring(1)
                : s3ObjectKey;

        // URL 결합
        return cdnBaseUrl + "/" + cleanPath;
    }
}