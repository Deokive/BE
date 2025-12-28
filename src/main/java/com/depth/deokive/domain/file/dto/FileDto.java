package com.depth.deokive.domain.file.dto;

import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class FileDto {
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "파일 업로드 요청 DTO")
    public static class UploadFileRequest {
        @Schema(description = "원본 파일명", example = "image.jpg")
        private String originalFileName;
        
        @Schema(description = "MIME 타입", example = "image/jpeg")
        private String mimeType;
        
        @Schema(description = "파일 크기 (bytes)", example = "102400")
        private Long fileSize;
        
        @Schema(description = "미디어 역할 (CONTENT: 본문, PREVIEW: 썸네일/대표)", example = "CONTENT")
        private MediaRole mediaRole; // CONTENT or PREVIEW
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "다중 파일 업로드 요청 DTO")
    public static class UploadFilesRequest {
        @Schema(description = "업로드할 파일 리스트")
        private List<UploadFileRequest> files;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "파일 업로드 응답 DTO")
    public static class UploadFileResponse {
        @Schema(description = "파일 아이디", example = "1")
        private Long fileId;
        
        @Schema(description = "저장된 파일명", example = "uuid_filename.jpg")
        private String filename;

        @Schema(description = "CDN URL (파일 경로)", example = "https://cdn.example.com/files/uuid_filename.jpg")
        private String cdnUrl; // filePath

        @Schema(description = "파일 크기 (bytes)", example = "102400")
        private Long fileSize;
        
        @Schema(description = "미디어 타입", example = "IMAGE")
        private String mediaType;
        
        @Schema(description = "미디어 역할", example = "CONTENT")
        private MediaRole mediaRole;
        
        @Schema(description = "정렬 순서", example = "0")
        private Integer sequence;

        public static FileDto.UploadFileResponse of (
                File file,
                FileDto.CompleteMultipartUploadRequest request
        ) {
            return FileDto.UploadFileResponse.builder()
                    .fileId(file.getId())
                    .filename(file.getFilename())
                    .cdnUrl(file.getFilePath())
                    .fileSize(file.getFileSize())
                    .mediaType(file.getMediaType().name())
                    .mediaRole(request.getMediaRole())
                    .sequence(request.getSequence())
                    .build();
        }
    }

    // DESCRIPTION: 단일 업로드를 병렬 처리하는게 더 속도 있음 -> 불필요한 DTO일 지도 모름
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "다중 파일 업로드 응답 DTO")
    public static class UploadFilesResponse {
        @Schema(description = "업로드된 파일 리스트")
        private List<UploadFileResponse> files;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "멀티파트 업로드 초기화 요청 DTO")
    public static class MultipartUploadInitiateRequest {
        @Schema(description = "원본 파일명", example = "large_video.mp4")
        private String originalFileName;
        
        @Schema(description = "MIME 타입", example = "video/mp4")
        private String mimeType;
        
        @Schema(description = "파일 크기 (bytes)", example = "104857600")
        private Long fileSize;
        
        @Schema(description = "미디어 역할", example = "CONTENT")
        private MediaRole mediaRole;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "멀티파트 업로드 초기화 응답 DTO")
    public static class MultipartUploadInitiateResponse {
        @Schema(description = "S3 객체 키", example = "files/uuid_filename.mp4")
        private String key;
        
        @Schema(description = "업로드 ID", example = "upload-id-12345")
        private String uploadId;
        
        @Schema(description = "Content-Type", example = "video/mp4")
        private String contentType;
        
        @Schema(description = "Part 개수", example = "5")
        private Integer partCount;
        
        @Schema(description = "Part별 Presigned URL 리스트")
        private List<PartPresignedUrl> partPresignedUrls;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Part Presigned URL DTO")
    public static class PartPresignedUrl {
        @Schema(description = "Part 번호", example = "1")
        private Integer partNumber;
        
        @Schema(description = "Presigned URL", example = "https://s3.amazonaws.com/...")
        private String presignedUrl;
        
        @Schema(description = "Part 크기 (bytes)", example = "20971520")
        private Long contentLength;  // Part 크기 (프론트엔드 검증용)
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "멀티파트 업로드 완료 요청 DTO")
    public static class CompleteMultipartUploadRequest {
        @Schema(description = "S3 객체 키", example = "files/uuid_filename.mp4")
        private String key;
        
        @Schema(description = "업로드 ID", example = "upload-id-12345")
        private String uploadId;
        
        @Schema(description = "업로드된 Part 리스트")
        private List<Part> parts;
        
        @Schema(description = "원본 파일명", example = "large_video.mp4")
        private String originalFileName;
        
        @Schema(description = "파일 크기 (bytes)", example = "104857600")
        private Long fileSize;
        
        @Schema(description = "MIME 타입", example = "video/mp4")
        private String mimeType;
        
        @Schema(description = "미디어 역할", example = "CONTENT")
        private MediaRole mediaRole;

        @Schema(description = "정렬 순서", example = "0")
        private Integer sequence;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Part 정보 DTO")
    public static class Part {
        @Schema(description = "Part 번호", example = "1")
        private Integer partNumber;
        
        @Schema(description = "ETag", example = "\"etag-value-12345\"")
        private String etag;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "멀티파트 업로드 완료 응답 DTO")
    public static class CompleteMultipartUploadResponse {
        @Schema(description = "파일 아이디", example = "1")
        private Long fileId;
        
        @Schema(description = "저장된 파일명", example = "uuid_filename.mp4")
        private String filename;
        
        @Schema(description = "CDN URL", example = "https://cdn.example.com/files/uuid_filename.mp4")
        private String cdnUrl;
        
        @Schema(description = "파일 크기 (bytes)", example = "104857600")
        private Long fileSize;
        
        @Schema(description = "미디어 타입", example = "VIDEO")
        private String mediaType;
        
        @Schema(description = "미디어 역할", example = "CONTENT")
        private MediaRole mediaRole;
        
        @Schema(description = "정렬 순서", example = "0")
        private Integer sequence;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "멀티파트 업로드 취소 요청 DTO")
    public static class MultipartUploadAbortRequest {
        @Schema(description = "S3 객체 키")
        private String key;

        @Schema(description = "업로드 ID")
        private String uploadId;
    }
}
