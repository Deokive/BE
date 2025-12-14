package com.depth.deokive.domain.file.dto;

import com.depth.deokive.domain.file.entity.MediaRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class FileDto {
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadFileRequest {
        private String originalFileName;
        private String mimeType;
        private Long fileSize;
        private MediaRole mediaRole; // CONTENT or PREVIEW
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadFilesRequest {
        private List<UploadFileRequest> files;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadFileResponse {
        private Long fileId;
        private String filename;
        private String cdnUrl; // filePath
        private Long fileSize;
        private String mediaType;
        private MediaRole mediaRole;
        private Integer sequence;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadFilesResponse {
        private List<UploadFileResponse> files;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MultipartUploadInitiateRequest {
        private String originalFileName;
        private String mimeType;
        private Long fileSize;
        private MediaRole mediaRole;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MultipartUploadInitiateResponse {
        private String key;
        private String uploadId;
        private String contentType;
        private Integer partCount;
        private List<PartPresignedUrl> partPresignedUrls;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PartPresignedUrl {
        private Integer partNumber;
        private String presignedUrl;
        private Long contentLength;  // Part 크기 (프론트엔드 검증용)
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CompleteMultipartUploadRequest {
        private String key;
        private String uploadId;
        private List<Part> parts;
        private String originalFileName;
        private Long fileSize;
        private String mimeType;
        private MediaRole mediaRole;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Part {
        private Integer partNumber;
        private String etag;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CompleteMultipartUploadResponse {
        private Long fileId;
        private String filename;
        private String cdnUrl;
        private Long fileSize;
        private String mediaType;
        private MediaRole mediaRole;
        private Integer sequence;
    }
}
