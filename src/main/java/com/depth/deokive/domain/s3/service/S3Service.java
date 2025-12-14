package com.depth.deokive.domain.s3.service;

import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // -------- AWS S3 Multipart Upload Methods : 모든 파일 타입 (이미지, 동영상 등) ---------
    // 1️⃣ 업로드 초기화
    public S3ServiceDto.UploadInitiateResponse initiateUpload(S3ServiceDto.UploadInitiateRequest request) {
        log.info("🚀 [S3Service] 멀티파트 업로드 초기화 요청 - filename: {}, size: {} bytes, mimeType: {}",
                request.getOriginFileName(), request.getFileSize(), request.getMimeType());

        // MediaType에 따라 폴더 결정 (이미지: files, 동영상: videos)
        String subFolder = determineSubFolder(request.getMimeType(), request.getOriginFileName());
        log.info("📁 [S3Service] 폴더 결정 - subFolder: {}", subFolder);

        // 서버가 Key 확정 (충돌/보안 대비)
        String key = generateKey(subFolder, request.getOriginFileName());
        log.info("🔑 [S3Service] S3 Key 생성 - key: {}", key);

        // Content-Type 결정
        String contentType = guessContentType(request.getMimeType(), request.getOriginFileName());
        log.info("📄 [S3Service] Content-Type 결정 - contentType: {}", contentType);

        // Multipart Upload 시작 요청
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .storageClass(StorageClass.STANDARD)
                .build();

        // Upload ID 응답
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(createRequest);
        log.info("✅ [S3Service] S3 멀티파트 업로드 초기화 완료 - uploadId: {}, key: {}",
                response.uploadId(), key);

        return S3ServiceDto.UploadInitiateResponse.builder()
                .uploadId(response.uploadId())
                .key(key)
                .contentType(contentType)
                .build();
    }

    // 2️⃣ Presigned URL 발급
    public URL generatePresignedUrl(S3ServiceDto.PresignedUrlRequest request) {
        log.debug("🔐 [S3Service] Presigned URL 생성 요청 - key: {}, uploadId: {}, partNumber: {}, contentLength: {}",
                request.getKey(), request.getUploadId(), request.getPartNumber(), request.getContentLength());

        // Upload ID와 PartNumber로 Presigned URL 생성 요청 준비
        var uploadPartRequestBuilder = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(request.getKey())
                .uploadId(request.getUploadId())
                .partNumber(request.getPartNumber());

        // contentLength가 제공되면 포함 (보안 강화 및 CORS preflight 방지)
        if (request.getContentLength() != null && request.getContentLength() > 0) {
            uploadPartRequestBuilder.contentLength(request.getContentLength());
            log.debug("📏 [S3Service] contentLength 포함 - {} bytes", request.getContentLength());
        }

        UploadPartRequest uploadPartRequest = uploadPartRequestBuilder.build();

        // Presigned URL 생성
        UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10)) // Presigned URL 유효 기간 설정
                .uploadPartRequest(uploadPartRequest)
                .build();

        // 3️⃣ PresignedURL part 업로드
        PresignedUploadPartRequest presignedRequest = s3Presigner.presignUploadPart(presignRequest);

        log.debug("✅ [S3Service] Presigned URL 생성 완료 - partNumber: {}, contentLength: {}",
                request.getPartNumber(), request.getContentLength());

        return presignedRequest.url();
    }

    // 4️⃣ Part 별 Presigned URL 발급
    public List<S3ServiceDto.PartPresignedUrlResponse> generatePartPresignedUrls(
            S3ServiceDto.PartPresignedUrlRequest request
    ) {
        log.info("🔑 [S3Service] Part별 Presigned URL 생성 시작 - key: {}, uploadId: {}, fileSize: {}",
                request.getKey(), request.getUploadId(), request.getFileSize());

        // Part Count 계산
        var partCount = calculatePartCount(request.getFileSize());
        log.info("📊 [S3Service] Part 개수 계산 - partCount: {}", partCount);

        List<S3ServiceDto.PartPresignedUrlResponse> partPresignedUrlResponses = new ArrayList<>();
        for (int part = 1; part <= partCount; part++) {
            // 각 Part의 정확한 크기 계산
            long partSize = calculatePartSize(request.getFileSize(), part, partCount);
            log.debug("📏 [S3Service] Part {} 크기 계산 - {} bytes", part, partSize);

            // 1) Presigned URL 발급 요청 DTO 생성 (contentLength 포함)
            S3ServiceDto.PresignedUrlRequest presignedUrlRequest =
                    S3ServiceDto.PresignedUrlRequest.builder()
                            .key(request.getKey())
                            .uploadId(request.getUploadId())
                            .partNumber(part)
                            .contentLength(partSize)
                            .build();

            // 2) Presigned URL 생성
            URL presignedUrl = generatePresignedUrl(presignedUrlRequest);

            // 3) 응답 리스트에 추가 (contentLength 포함)
            partPresignedUrlResponses.add(
                    S3ServiceDto.PartPresignedUrlResponse.builder()
                            .partNumber(part)
                            .presignedUrl(presignedUrl.toString())
                            .contentLength(partSize)
                            .build()
            );
        }

        log.info("✅ [S3Service] Part별 Presigned URL 생성 완료 - 총 {}개", partPresignedUrlResponses.size());
        return partPresignedUrlResponses;
    }

    // 5️⃣ 업로드 완료
    public CompleteMultipartUploadResponse completeUpload(S3ServiceDto.CompleteUploadRequest request) {
        log.info("🏁 [S3Service] 멀티파트 업로드 완료 요청 - key: {}, uploadId: {}, parts 개수: {}",
                request.getKey(), request.getUploadId(), request.getParts().size());

        // partNumber 오름차순 정렬 필수 -> Prevent InvalidPartOrder Error
        List<CompletedPart> completedParts = request.getParts().stream()
                .sorted(Comparator.comparing(S3ServiceDto.CompleteUploadRequest.Part::getPartNumber))
                .map(p -> {
                    log.info("🏷️ [S3Service] Part {} ETag 교환 - partNumber: {}, eTag: {}",
                            p.getPartNumber(), p.getPartNumber(), p.getEtag());
                    return CompletedPart.builder()
                            .partNumber(p.getPartNumber())
                            .eTag(p.getEtag()) // 수정: getter 일치
                            .build();
                })
                .toList();

        // 재조립
        CompletedMultipartUpload multipart = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();

        // 업로드 완료 요청
        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(request.getKey())
                .uploadId(request.getUploadId())
                .multipartUpload(multipart)
                .build();

        CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(completeRequest);
        log.info("✅ [S3Service] 멀티파트 업로드 완료 - location: {}, eTag: {}",
                response.location(), response.eTag());

        return response;
    }

    // 5️⃣ 업로드 취소
    public void abortUpload(S3ServiceDto.AbortUploadRequest request) {
        log.info("🛑 [S3Service] 멀티파트 업로드 취소 요청 - key: {}, uploadId: {}",
                request.getKey(), request.getUploadId());

        AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(request.getKey())
                .uploadId(request.getUploadId())
                .build();

        s3Client.abortMultipartUpload(abortRequest);
        log.info("✅ [S3Service] 멀티파트 업로드 취소 완료 - key: {}, uploadId: {}",
                request.getKey(), request.getUploadId());
    }

    // MultipartCountCalculator
    public Integer calculatePartCount(Long fileSize) {
        final long MIN_PART_SIZE = 5L * 1024 * 1024; // 5MB
        final long MAX_PART_SIZE = 2L * 1024 * 1024 * 1024; // 2GB
        final long MAX_PART_COUNT = 10_000L;
        final long RECOMMENDED_PART_SIZE = getRecommendedPartSize(fileSize);

        if (fileSize <= 0) { return 1; }

        // RECOMMENDED_PART_SIZE 미만이면 멀티파트 불필요
        if (fileSize < RECOMMENDED_PART_SIZE) { return 1; }

        // 1. RECOMMENDED_PART_SIZE 기준으로 계산
        long partCount = (fileSize + RECOMMENDED_PART_SIZE - 1) / RECOMMENDED_PART_SIZE; // 올림 계산

        // 2. MAX_PART_COUNT 제약 확인
        if (partCount > MAX_PART_COUNT) {
            // MAX_PART_COUNT로 제한하면 각 파트가 MAX_PART_SIZE를 초과할 수 있음
            // 따라서 MAX_PART_SIZE 기준으로 재계산
            partCount = (fileSize + MAX_PART_SIZE - 1) / MAX_PART_SIZE; // 올림 계산
        }

        // 3. MIN_PART_SIZE 제약 확인 (거의 발생하지 않지만 안전장치)
        long partSize = fileSize / partCount;
        if (partSize < MIN_PART_SIZE && partCount > 1) {
            // MIN_PART_SIZE 기준으로 재계산
            partCount = fileSize / MIN_PART_SIZE;
            if (partCount == 0) partCount = 1;
        }

        return (int) Math.min(partCount, MAX_PART_COUNT);
    }



    // -------- Helper Methods --------

    /** Key 생성 (폴더명 + UUID + 원본파일명) */
    private String generateKey(String subFolder, String originalFileName) {
        String safeName = (originalFileName == null || originalFileName.isBlank()) ? "unknown" : originalFileName;
        safeName = safeName.replaceAll("[^a-zA-Z0-9._-]", ""); // Key 이름에 안전하지 않은 문자 제거
        return subFolder + "/" + UUID.randomUUID() + "__" + safeName;
    }

    /** Content-Type 추론 */
    private String guessContentType(String mimeType, String fileName) {
        String contentType = mimeType;
        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(fileName);
            if (contentType == null) contentType = "application/octet-stream";
        }
        return contentType;
    }

    private static long getRecommendedPartSize(Long fileSize) {
        final long RECOMMENDED_PART_SIZE;

        // 파일 크기에 따라 적정 파트 크기 결정
        // 1GB 미만 -> 64MB, 1GB~5GB -> 128MB
        if (fileSize < 1024L * 1024 * 1024) { RECOMMENDED_PART_SIZE = 64L * 1024 * 1024; }
        else { RECOMMENDED_PART_SIZE = 128L * 1024 * 1024; }

        return RECOMMENDED_PART_SIZE;
    }

    /**
     * 각 Part의 정확한 크기 계산
     * @param fileSize 전체 파일 크기
     * @param partNumber Part 번호 (1부터 시작)
     * @param totalParts 전체 Part 개수
     * @return 해당 Part의 크기 (bytes)
     */
    private long calculatePartSize(Long fileSize, int partNumber, int totalParts) {
        if (totalParts == 1) {
            return fileSize;
        }

        // 기본 Part 크기 (마지막 Part 제외)
        long basePartSize = fileSize / totalParts;

        // 마지막 Part는 나머지 모두 포함
        if (partNumber == totalParts) {
            long remainingSize = fileSize - (basePartSize * (totalParts - 1));
            return remainingSize;
        }

        return basePartSize;
    }

    /**
     * MediaType에 따라 S3 폴더 결정
     * 이미지: files, 동영상: videos
     *
     * @param mimeType MIME 타입
     * @param fileName 파일명
     * @return 폴더명 ("files" 또는 "videos")
     */
    private String determineSubFolder(String mimeType, String fileName) {
        if (mimeType == null && fileName == null) {
            return "files"; // 기본값
        }

        String type = mimeType != null ? mimeType.toLowerCase() : "";
        String name = fileName != null ? fileName.toLowerCase() : "";

        // 동영상인 경우 videos 폴더 사용
        if (type.startsWith("video/") || name.matches(".*\\.(mp4|avi|mov|wmv|flv|webm|mkv)$")) {
            return "videos";
        }

        // 그 외는 files 폴더 사용 (이미지, 오디오 등)
        return "files";
    }
}