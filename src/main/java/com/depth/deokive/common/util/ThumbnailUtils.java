package com.depth.deokive.common.util;

public final class ThumbnailUtils {
    private static final String THUMBNAIL = "thumbnail";
    private static final String MEDIUM = "medium";

    private static final String THUMBNAIL_ROOT_PATH = "files/thumbnails/";

    private ThumbnailUtils() {}

    public static String getSmallThumbnailKey(String originalKey) {
        return generateThumbnailKey(originalKey, THUMBNAIL);
    }

    public static String getMediumThumbnailKey(String originalKey) {
        return generateThumbnailKey(originalKey, MEDIUM);
    }

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
}