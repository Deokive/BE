package com.depth.deokive.domain.file.service;

import com.depth.deokive.domain.file.dto.FileDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import com.depth.deokive.domain.s3.service.S3Service;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 순수 파일 관리 서비스
 * S3 업로드, File 엔티티 관리, CDN URL 생성 등 파일 관련 기능만 담당
 * Entity-File 매핑은 FileAttachmentService에서 처리
 *
 * 썸네일 처리 방식: 패턴 4 (동적 썸네일 URL 생성)
 * - 썸네일 파일을 DB에 저장하지 않음
 * - 조회 시 원본 파일의 S3 키를 기반으로 썸네일 URL을 동적으로 생성
 * - S3에 썸네일이 있으면 해당 URL 반환, 없으면 원본 URL 사용 (fallback)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FileService {
    private final S3Service s3Service;
    private final FileRepository fileRepository;

    @Value("${cdn.base-url:#{null}}")
    private String cdnBaseUrl;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    /**
     * 멀티파트 업로드 초기화 (모든 파일 타입: 이미지, 동영상 등)
     * PresignedUrl 방식으로 통일
     */
    public FileDto.MultipartUploadInitiateResponse initiateMultipartUpload(
            FileDto.MultipartUploadInitiateRequest request
    ) {
        log.info("🚀 [FileService] 멀티파트 업로드 초기화 시작 - filename: {}, size: {} bytes",
                request.getOriginalFileName(), request.getFileSize());

        // S3 멀티파트 업로드 초기화
        S3ServiceDto.UploadInitiateRequest s3Request = S3ServiceDto.UploadInitiateRequest.builder()
                .originFileName(request.getOriginalFileName())
                .mimeType(request.getMimeType())
                .fileSize(request.getFileSize())
                .build();

        S3ServiceDto.UploadInitiateResponse s3Response = s3Service.initiateUpload(s3Request);
        log.info("✅ [FileService] S3 멀티파트 업로드 초기화 완료 - key: {}, uploadId: {}",
                s3Response.getKey(), s3Response.getUploadId());

        // Part 개수 계산
        Integer partCount = s3Service.calculatePartCount(request.getFileSize());
        log.info("📊 [FileService] Part 개수 계산 완료 - partCount: {}", partCount);

        // 각 Part에 대한 Presigned URL 생성
        S3ServiceDto.PartPresignedUrlRequest partRequest = S3ServiceDto.PartPresignedUrlRequest.builder()
                .key(s3Response.getKey())
                .uploadId(s3Response.getUploadId())
                .fileSize(request.getFileSize())
                .build();

        List<S3ServiceDto.PartPresignedUrlResponse> partPresignedUrls = s3Service.generatePartPresignedUrls(partRequest);
        log.info("🔑 [FileService] Presigned URL 생성 완료 - 총 {}개", partPresignedUrls.size());

        // DTO 변환
        List<FileDto.PartPresignedUrl> partPresignedUrlList = partPresignedUrls.stream()
                .map(p -> FileDto.PartPresignedUrl.builder()
                        .partNumber(p.getPartNumber())
                        .presignedUrl(p.getPresignedUrl())
                        .contentLength(p.getContentLength())
                        .build())
                .collect(Collectors.toList());

        log.info("🎯 [FileService] 멀티파트 업로드 초기화 응답 준비 완료 - key: {}, uploadId: {}, partCount: {}",
                s3Response.getKey(), s3Response.getUploadId(), partCount);

        return FileDto.MultipartUploadInitiateResponse.builder()
                .key(s3Response.getKey())
                .uploadId(s3Response.getUploadId())
                .contentType(s3Response.getContentType())
                .partCount(partCount)
                .partPresignedUrls(partPresignedUrlList)
                .build();
    }

    /**
     * 멀티파트 업로드 완료 및 DB 저장
     * 순수 파일 업로드만 담당, Entity-File 연결은 FileAttachmentService에서 처리
     *
     * @param key S3 object key
     * @param uploadId 업로드 ID
     * @param parts Part 목록
     * @param originalFileName 원본 파일명
     * @param fileSize 파일 크기
     * @param mimeType MIME 타입
     * @param mediaRole MediaRole (PREVIEW인 경우 isThumbnail = true로 설정)
     * @return 업로드된 File 엔티티
     */
    public File completeMultipartUpload(
            String key,
            String uploadId,
            List<FileDto.Part> parts,
            String originalFileName,
            Long fileSize,
            String mimeType,
            MediaRole mediaRole
    ) {
        log.info("🏁 [FileService] 멀티파트 업로드 완료 요청 - key: {}, uploadId: {}, parts 개수: {}",
                key, uploadId, parts.size());

        // ETag 로깅
        log.info("🏷️ [FileService] ETag 목록:");
        parts.forEach(part ->
                log.info("  - Part {}: ETag = {}", part.getPartNumber(), part.getEtag())
        );

        // S3 멀티파트 업로드 완료
        S3ServiceDto.CompleteUploadRequest s3Request = S3ServiceDto.CompleteUploadRequest.builder()
                .key(key)
                .uploadId(uploadId)
                .parts(parts.stream()
                        .map(p -> S3ServiceDto.CompleteUploadRequest.Part.builder()
                                .partNumber(p.getPartNumber())
                                .etag(p.getEtag())
                                .build())
                        .collect(Collectors.toList()))
                .build();

        CompleteMultipartUploadResponse s3Response = s3Service.completeUpload(s3Request);
        log.info("✅ [FileService] S3 멀티파트 업로드 완료 - location: {}, etag: {}",
                s3Response.location(), s3Response.eTag());

        // 업로드된 파일의 URL 가져오기
        String s3Url = s3Response.location();
        String cdnUrl = generateCdnUrl(s3Url);

        // MediaType 결정
        MediaType mediaType = determineMediaType(mimeType, originalFileName);

        // MediaRole.PREVIEW인 경우 isThumbnail = true로 설정 (대표 이미지로 지정)
        boolean isThumbnail = (mediaRole == MediaRole.PREVIEW);

        // File 엔티티 저장 (원본 파일만 저장, 썸네일은 DB에 저장하지 않음 - 패턴 4)
        // 단, MediaRole.PREVIEW인 경우 isThumbnail = true로 설정하여 대표 이미지임을 표시
        File fileEntity = File.builder()
                .s3ObjectKey(key)
                .filename(originalFileName)
                .filePath(cdnUrl)
                .fileSize(fileSize)
                .mediaType(mediaType)
                .isThumbnail(isThumbnail)
                .build();

        fileEntity = fileRepository.save(fileEntity);
        log.info("💾 [FileService] File 엔티티 저장 완료 - fileId: {}, filename: {}",
                fileEntity.getId(), fileEntity.getFilename());

        // 참고: 썸네일은 DB에 저장하지 않고, 조회 시 getThumbnailUrl()로 동적 생성 (패턴 4)

        log.info("🎉 [FileService] 멀티파트 업로드 완료 - fileId: {}", fileEntity.getId());

        return fileEntity;
    }

    /** 멀티파트 업로드 취소 */
    public void abortMultipartUpload(String key, String uploadId) {
        log.info("🛑 [FileService] 멀티파트 업로드 취소 요청 - key: {}, uploadId: {}", key, uploadId);
        S3ServiceDto.AbortUploadRequest request = S3ServiceDto.AbortUploadRequest.builder()
                .key(key)
                .uploadId(uploadId)
                .build();
        s3Service.abortUpload(request);
        log.info("✅ [FileService] 멀티파트 업로드 취소 완료 - key: {}, uploadId: {}", key, uploadId);
    }

    @Transactional(readOnly = true)
    public File validateFileOwner(Long fileId, Long userId) {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RestException(ErrorCode.FILE_NOT_FOUND));

        // 생성자(createdBy)와 요청자(userId) 비교
        if (!file.getCreatedBy().equals(userId)) {
            log.warn("⚠️ IDOR Attempt Detected! FileId: {}, RequestUser: {}, Owner: {}",
                    fileId, userId, file.getCreatedBy());
            throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 혹은 FILE_ACCESS_DENIED
        }

        return file;
    }

    // -------- Helper Methods --------

    /** MediaType 결정 */
    private MediaType determineMediaType(String mimeType, String fileName) {
        if (mimeType == null && fileName == null) {
            return MediaType.UNKNOWN;
        }

        String type = mimeType != null ? mimeType.toLowerCase() : "";
        String name = fileName != null ? fileName.toLowerCase() : "";

        if (type.startsWith("video/") || name.matches(".*\\.(mp4|avi|mov|wmv|flv|webm|mkv)$")) {
            return MediaType.VIDEO;
        } else if (type.startsWith("image/") || name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|svg)$")) {
            return MediaType.IMAGE;
        } else if (type.startsWith("audio/") || name.matches(".*\\.(mp3|wav|flac|aac|ogg)$")) {
            return MediaType.MUSIC;
        } else {
            return MediaType.UNKNOWN;
        }
    }

    /**
     * CDN URL 생성
     * CDN base URL이 필수로 설정되어 있어야 함 (보안: 버킷명 노출 방지)
     *
     * @param s3Url S3 URL
     * @return CDN URL
     * @throws IllegalStateException CDN base URL이 설정되지 않은 경우
     */
    private String generateCdnUrl(String s3Url) {
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "CDN base URL이 설정되지 않았습니다. " + "보안을 위해 CDN 설정이 필수입니다. "
            );
        }

        // S3 URL에서 key 추출하여 CDN URL로 변환
        // 예: https://bucket.s3.region.amazonaws.com/key -> https://cdn.example.com/key
        String key = extractKeyFromS3Url(s3Url);
        return buildCdnUrl(key);
    }

    /** S3 URL에서 key 추출 */
    private String extractKeyFromS3Url(String s3Url) {
        try {
            java.net.URI uri = java.net.URI.create(s3Url);
            String path = uri.getPath();
            // 첫 번째 '/' 제거
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (Exception e) {
            return s3Url;
        }
    }

    /**
     * CDN URL 생성 헬퍼 메서드
     * cdnBaseUrl의 마지막 '/' 제거 후 경로와 결합하여 올바른 URL 생성
     *
     * @param path 경로 (예: "files/..." 또는 "files/thumbnails/...")
     * @return 완성된 CDN URL
     */
    private String buildCdnUrl(String path) {
        // CDN base URL 정리 (마지막 '/' 제거)
        String baseUrl = cdnBaseUrl.endsWith("/")
                ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1)
                : cdnBaseUrl;

        // 경로 정리 (앞의 '/' 제거)
        String cleanPath = path.startsWith("/")
                ? path.substring(1)
                : path;

        // URL 결합
        return baseUrl + "/" + cleanPath;
    }

    /**
     * 썸네일 URL 생성 (리사이징 버킷 → CDN)
     * 원본 File에서 썸네일 URL을 동적으로 생성 (패턴 4)
     *
     * CDN base URL이 필수로 설정되어 있어야 함 (보안: 버킷명 노출 방지)
     *
     * @param file 원본 File 엔티티
     * @param size 썸네일 크기 ("thumbnail" 또는 "medium")
     * @return 썸네일 CDN URL
     * @throws IllegalStateException CDN base URL이 설정되지 않은 경우
     */
    public String getThumbnailUrl(File file, String size) {
        if (file.getMediaType() != MediaType.IMAGE) {
            return null; // 이미지가 아니면 썸네일 없음
        }

        // 원본 키에서 썸네일 키 생성
        String originalKey = file.getS3ObjectKey();
        String thumbnailKey = generateThumbnailKey(originalKey, size);

        // CDN URL 생성 (CloudFront의 /files/thumbnails/* 패턴 사용)
        // CDN이 필수이므로 설정되지 않으면 예외 발생
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "CDN base URL이 설정되지 않았습니다. " +
                            "보안을 위해 CDN 설정이 필수입니다. " +
                            "application.yml에 cdn.base-url을 설정해주세요."
            );
        }

        // CDN base URL에 썸네일 키를 직접 추가
        // 예: https://cdn.example.com/files/thumbnails/thumbnail/{UUID}__{filename}.jpg
        return buildCdnUrl(thumbnailKey);
    }

    /**
     * 썸네일 키 생성
     * 원본: files/{UUID}__{filename}
     * 결과: files/thumbnails/{size}/{UUID}__{filename}
     */
    private String generateThumbnailKey(String originalKey, String size) {
        // files/{UUID}__{filename}에서 파일명 추출
        String fileName = originalKey.substring(originalKey.lastIndexOf("/") + 1);

        // files/thumbnails/{size}/{UUID}__{filename} 생성
        return "files/thumbnails/" + size + "/" + fileName;
    }
}