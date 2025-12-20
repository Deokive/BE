package com.depth.deokive.common.util;

import java.net.URI;

public final class ThumbnailUtils {
    private static final String THUMBNAIL = "thumbnail";
    private static final String MEDIUM = "medium";

    private ThumbnailUtils() {}

    public static String getSmallThumbnailUrl(String originalCdnUrl) {
        return generateThumbnailUrl(originalCdnUrl, THUMBNAIL);
    }

    public static String getMediumThumbnailUrl(String originalCdnUrl) {
        return generateThumbnailUrl(originalCdnUrl, MEDIUM);
    }

    private static String generateThumbnailUrl(String originalCdnUrl, String size) {
        if (originalCdnUrl == null || originalCdnUrl.isBlank()) {
            return null;
        }

        // 원본 CDN URL에서 base URL과 경로 추출
        // 예: "https://cdn.example.com/files/{UUID}__{filename}"
        URI uri;
        try {
            uri = URI.create(originalCdnUrl);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CDN URL: " + originalCdnUrl, e);
        }

        String baseUrl = uri.getScheme() + "://" + uri.getAuthority();
        String originalPath = uri.getPath();

        // 경로에서 파일명 추출
        // 예: "/files/{UUID}__{filename}" -> "{UUID}__{filename}"
        String fileName = originalPath.substring(originalPath.lastIndexOf("/") + 1);

        // 썸네일 경로 생성
        // 예: "/files/thumbnails/{size}/{UUID}__{filename}"
        String thumbnailPath = "/files/thumbnails/" + size + "/" + fileName;

        // CDN URL 생성
        return baseUrl + thumbnailPath;
    }
}