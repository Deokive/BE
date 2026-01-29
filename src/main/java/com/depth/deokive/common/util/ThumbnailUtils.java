package com.depth.deokive.common.util;

public final class ThumbnailUtils {
    private static final String THUMBNAIL = "thumbnail";
    private static final String MEDIUM = "medium";

    private static final String THUMBNAIL_ROOT_PATH = "files/thumbnails/";
    private static final String VIDEO_THUMBNAIL_ROOT_PATH = "videos/thumbnails/";

    private ThumbnailUtils() {}

    public static String getSmallThumbnailKey(String originalKey) {
        // 동영상은 small thumbnail 미지원 (이미지만 처리)
        return generateThumbnailKey(originalKey, THUMBNAIL);
    }

    public static String getMediumThumbnailKey(String originalKey) {
        // 동영상/이미지 자동 분기 처리
        if (isVideo(originalKey)) {
            return generateVideoThumbnailKey(originalKey, MEDIUM);
        }
        return generateThumbnailKey(originalKey, MEDIUM);
    }

    // --------- Image Thumbnail 처리 (기존 로직) ------------------

    private static String generateThumbnailKey(String originalKey, String size) {
        if (originalKey == null || originalKey.isBlank()) {
            return null;
        }

        // 1. 파일명 추출 (경로 구분자 '/' 뒤의 문자열)
        // ex: "files/images/my-pic.jpg" -> "my-pic.jpg"
        int lastSlashIndex = originalKey.lastIndexOf("/");
        String fileName = (lastSlashIndex == -1)
                ? originalKey
                : originalKey.substring(lastSlashIndex + 1);

        // 2. 썸네일 경로 조립
        // ex: "files/thumbnails/" + "medium" + "/" + "my-pic.jpg"
        return THUMBNAIL_ROOT_PATH + size + "/" + fileName;
    }

    // -------- Video Thumbnail 처리 ---------

    private static String generateVideoThumbnailKey(String originalKey, String size) {
        if (originalKey == null || originalKey.isBlank()) {
            return null;
        }

        int lastSlashIndex = originalKey.lastIndexOf("/");
        String fileName = (lastSlashIndex == -1)
                ? originalKey
                : originalKey.substring(lastSlashIndex + 1);

        String jpgFileName = replaceExtensionToJpg(fileName);

        return VIDEO_THUMBNAIL_ROOT_PATH + size + "/" + jpgFileName;
    }

    private static boolean isVideo(String originalKey) {
        return originalKey != null && originalKey.startsWith("videos/");
    }

    private static String replaceExtensionToJpg(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex <= 0 || lastDotIndex == fileName.length() - 1) {
            return fileName + ".jpg";
        }
        return fileName.substring(0, lastDotIndex) + ".jpg";
    }
}