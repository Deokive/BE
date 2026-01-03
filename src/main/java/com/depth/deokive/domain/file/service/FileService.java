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

import java.util.Collections;
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

    @Value("${cdn.base-url:#{null}}") private String cdnBaseUrl;

    /** 멀티파트 업로드 초기화 (모든 파일 타입: 이미지, 동영상 등) */
    public FileDto.MultipartUploadInitiateResponse initiateMultipartUpload(
            FileDto.MultipartUploadInitiateRequest request
    ) {
        log.info("1️⃣[FileService] 멀티파트 업로드 초기화 시작 - filename: {}, size: {} bytes",
                request.getOriginalFileName(), request.getFileSize());

        // S3 멀티파트 업로드 초기화
        S3ServiceDto.UploadInitiateRequest s3Request = S3ServiceDto.UploadInitiateRequest.builder()
                .originFileName(request.getOriginalFileName())
                .mimeType(request.getMimeType())
                .fileSize(request.getFileSize())
                .build();

        S3ServiceDto.UploadInitiateResponse s3Response = s3Service.initiateUpload(s3Request);
        log.info("2️⃣ [FileService] S3 멀티파트 업로드 초기화 완료 - key: {}, uploadId: {}",
                s3Response.getKey(), s3Response.getUploadId());

        // Part 개수 계산
        Integer partCount = s3Service.calculatePartCount(request.getFileSize());
        log.info("3️⃣ [FileService] Part 개수 계산 완료 - partCount: {}", partCount);

        // 각 Part에 대한 Presigned URL 생성
        S3ServiceDto.PartPresignedUrlRequest partRequest = S3ServiceDto.PartPresignedUrlRequest.builder()
                .key(s3Response.getKey())
                .uploadId(s3Response.getUploadId())
                .fileSize(request.getFileSize())
                .build();

        List<S3ServiceDto.PartPresignedUrlResponse> partPresignedUrls = s3Service.generatePartPresignedUrls(partRequest);
        log.info("4️⃣ [FileService] Presigned URL 생성 완료 - 총 {}개", partPresignedUrls.size());

        // DTO 변환
        List<FileDto.PartPresignedUrl> partPresignedUrlList = partPresignedUrls.stream()
                .map(p -> FileDto.PartPresignedUrl.builder()
                        .partNumber(p.getPartNumber())
                        .presignedUrl(p.getPresignedUrl())
                        .contentLength(p.getContentLength())
                        .build())
                .collect(Collectors.toList());

        log.info("🟢 [FileService] 멀티파트 업로드 초기화 응답 준비 완료 - key: {}, uploadId: {}, partCount: {}",
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
     * @param request CompleteMultipartUploadRequest
     * @return 저장된 File 엔티티
     */
    public File completeMultipartUpload(
            FileDto.CompleteMultipartUploadRequest request
    ) {
        log.info("🏁 [FileService] 멀티파트 업로드 완료 요청 - key: {}, uploadId: {}, parts 개수: {}",
                request.getKey(), request.getUploadId(), request.getParts().size());

        // ETag 로깅
        log.info("🏷️ [FileService] ETag 목록:");
        request.getParts().forEach(part ->
                log.info("  - Part {}: ETag = {}", part.getPartNumber(), part.getEtag())
        );

        // S3 멀티파트 업로드 완료
        S3ServiceDto.CompleteUploadRequest s3Request = S3ServiceDto.CompleteUploadRequest.builder()
                .key(request.getKey())
                .uploadId(request.getUploadId())
                .parts(request.getParts().stream()
                        .map(p -> S3ServiceDto.CompleteUploadRequest.Part.builder()
                                .partNumber(p.getPartNumber())
                                .etag(p.getEtag())
                                .build())
                        .collect(Collectors.toList()))
                .build();

        CompleteMultipartUploadResponse s3Response = s3Service.completeUpload(s3Request);
        log.info("1️⃣ [FileService] S3 멀티파트 업로드 완료 - location: {}, etag: {}",
                s3Response.location(), s3Response.eTag());

        // MediaType 결정
        MediaType mediaType = determineMediaType(request.getMimeType(), request.getOriginalFileName());

        // MediaRole.PREVIEW인 경우 isThumbnail = true로 설정 (대표 이미지로 지정)
        boolean isThumbnail = (request.getMediaRole() == MediaRole.PREVIEW);

        // File 엔티티 저장 (원본 파일만 저장, 썸네일은 DB에 저장하지 않음 - 패턴 4)
        // 단, MediaRole.PREVIEW인 경우 isThumbnail = true로 설정하여 대표 이미지임을 표시
        File fileEntity = File.builder()
                .s3ObjectKey(request.getKey())
                .filename(request.getOriginalFileName())
                .fileSize(request.getFileSize())
                .mediaType(mediaType)
                .isThumbnail(isThumbnail)
                .build();

        fileEntity = fileRepository.save(fileEntity);
        log.info("2️⃣ [FileService] File 엔티티 저장 완료 - fileId: {}, filename: {}",
                fileEntity.getId(), fileEntity.getFilename());

        log.info("🟢 [FileService] 멀티파트 업로드 완료 - fileId: {}", fileEntity.getId());

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
        log.info("🟢 [FileService] 멀티파트 업로드 취소 완료 - key: {}, uploadId: {}", key, uploadId);
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

    @Transactional(readOnly = true)
    public List<File> validateFileOwners(List<Long> fileIds, Long userId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<File> files = fileRepository.findAllById(fileIds); // Bulk Fetch

        // 개수 검증
        if (files.size() != fileIds.stream().distinct().count()) {
            throw new RestException(ErrorCode.FILE_NOT_FOUND);
        }

        // 소유권 검증
        boolean isAllMine = files.stream().allMatch(file -> file.getCreatedBy().equals(userId));

        if (!isAllMine) {
            log.warn("⚠️ IDOR Attempt Detected in Bulk Request! User: {}", userId);
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }

        return files;
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
}