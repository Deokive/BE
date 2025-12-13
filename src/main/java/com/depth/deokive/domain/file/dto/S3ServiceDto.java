package com.depth.deokive.domain.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Duration;

import java.util.List;

public class S3ServiceDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadInitiateRequest {
        private String originFileName; // 클라이언트 파일명
        private String mimeType;       // 명시적 Content-Type (선호)
        private Long fileSize;
        private FileType fileType;     // 선택적
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadInitiateResponse {
        private String key;           // 서버가 확정한 S3 객체 키
        private String uploadId;
        private String contentType;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PresignedUrlRequest {
        private String key;          // S3 객체 키
        private String uploadId;
        private Integer partNumber;
        private Long contentLength;  // Part 크기 (서명에 포함되어 보안 강화)
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CompleteUploadRequest {
        private String key;
        private String uploadId;
        private List<Part> parts;

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Part {
            private Integer partNumber;
            private String etag;
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AbortUploadRequest {
        private String key;
        private String uploadId;
    }

    // 일반 파일 업로드 결과 DTO
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadResult {
        private String key; // S3에 저장된 Key
        private String url; // S3에 저장된 파일의 전체 URL
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PartPresignedUrlRequest {
        private String key;
        private String uploadId;
        private Duration duration;
        private Long fileSize;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PartPresignedUrlResponse {
        private int partNumber;
        private String presignedUrl;
        private Long contentLength;  // Part 크기 (프론트엔드 검증용)
    }

    public enum FileType { IMAGE, VIDEO, AUDIO, PDF, OTHER }
}

