package com.depth.deokive.service;

import com.depth.deokive.domain.file.dto.FileDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import com.depth.deokive.domain.s3.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FileService 단위 테스트
 * Post 도메인 없이 File 도메인만 독립적으로 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileService 테스트")
class FileServiceTest {
    @Mock
    private S3Service s3Service;

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private FileService fileService;

    private static final String TEST_BUCKET_NAME = "test-bucket";
    private static final String TEST_CDN_BASE_URL = "https://cdn.example.com";
    private static final String TEST_S3_URL = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/files/test-key";

    @BeforeEach
    void setUp() {
        // @Value 필드 주입
        ReflectionTestUtils.setField(fileService, "bucketName", TEST_BUCKET_NAME);
        ReflectionTestUtils.setField(fileService, "cdnBaseUrl", TEST_CDN_BASE_URL);
    }

    @Test
    @DisplayName("멀티파트 업로드 초기화 - 이미지 파일")
    void testInitiateMultipartUpload_Image() throws Exception {
        // Given
        String originalFileName = "test-image.jpg";
        String mimeType = "image/jpeg";
        Long fileSize = 1024 * 1024L; // 1MB

        FileDto.MultipartUploadInitiateRequest request = FileDto.MultipartUploadInitiateRequest.builder()
                .originalFileName(originalFileName)
                .mimeType(mimeType)
                .fileSize(fileSize)
                .build();

        String expectedKey = "files/uuid-123__test-image.jpg";
        String expectedUploadId = "upload-id-123";
        String expectedContentType = "image/jpeg";
        Integer expectedPartCount = 1;

        // S3Service Mock 설정
        S3ServiceDto.UploadInitiateResponse s3InitiateResponse = S3ServiceDto.UploadInitiateResponse.builder()
                .key(expectedKey)
                .uploadId(expectedUploadId)
                .contentType(expectedContentType)
                .build();

        when(s3Service.initiateUpload(any(S3ServiceDto.UploadInitiateRequest.class)))
                .thenReturn(s3InitiateResponse);
        when(s3Service.calculatePartCount(fileSize)).thenReturn(expectedPartCount);

        // Presigned URL Mock 설정
        List<S3ServiceDto.PartPresignedUrlResponse> partPresignedUrls = new ArrayList<>();
        partPresignedUrls.add(S3ServiceDto.PartPresignedUrlResponse.builder()
                .partNumber(1)
                .presignedUrl("https://s3.amazonaws.com/presigned-url-1")
                .contentLength(fileSize)
                .build());

        when(s3Service.generatePartPresignedUrls(any(S3ServiceDto.PartPresignedUrlRequest.class)))
                .thenReturn(partPresignedUrls);

        // When
        FileDto.MultipartUploadInitiateResponse response = fileService.initiateMultipartUpload(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getKey()).isEqualTo(expectedKey);
        assertThat(response.getUploadId()).isEqualTo(expectedUploadId);
        assertThat(response.getContentType()).isEqualTo(expectedContentType);
        assertThat(response.getPartCount()).isEqualTo(expectedPartCount);
        assertThat(response.getPartPresignedUrls()).hasSize(1);
        assertThat(response.getPartPresignedUrls().get(0).getPartNumber()).isEqualTo(1);
        assertThat(response.getPartPresignedUrls().get(0).getPresignedUrl()).isNotNull();
        assertThat(response.getPartPresignedUrls().get(0).getContentLength()).isEqualTo(fileSize);

        // Verify
        verify(s3Service, times(1)).initiateUpload(any(S3ServiceDto.UploadInitiateRequest.class));
        verify(s3Service, times(1)).calculatePartCount(fileSize);
        verify(s3Service, times(1)).generatePartPresignedUrls(any(S3ServiceDto.PartPresignedUrlRequest.class));
    }

    @Test
    @DisplayName("멀티파트 업로드 초기화 - 대용량 동영상 파일 (여러 Part)")
    void testInitiateMultipartUpload_LargeVideo() throws Exception {
        // Given
        String originalFileName = "test-video.mp4";
        String mimeType = "video/mp4";
        Long fileSize = 500 * 1024 * 1024L; // 500MB (여러 Part로 분할)

        FileDto.MultipartUploadInitiateRequest request = FileDto.MultipartUploadInitiateRequest.builder()
                .originalFileName(originalFileName)
                .mimeType(mimeType)
                .fileSize(fileSize)
                .build();

        String expectedKey = "videos/uuid-456__test-video.mp4";
        String expectedUploadId = "upload-id-456";
        Integer expectedPartCount = 8; // 500MB / 64MB = 약 8개 Part

        // S3Service Mock 설정
        S3ServiceDto.UploadInitiateResponse s3InitiateResponse = S3ServiceDto.UploadInitiateResponse.builder()
                .key(expectedKey)
                .uploadId(expectedUploadId)
                .contentType("video/mp4")
                .build();

        when(s3Service.initiateUpload(any(S3ServiceDto.UploadInitiateRequest.class)))
                .thenReturn(s3InitiateResponse);
        when(s3Service.calculatePartCount(fileSize)).thenReturn(expectedPartCount);

        // 여러 Part의 Presigned URL Mock 설정
        List<S3ServiceDto.PartPresignedUrlResponse> partPresignedUrls = new ArrayList<>();
        long partSize = 64 * 1024 * 1024L; // 64MB
        for (int i = 1; i <= expectedPartCount; i++) {
            partPresignedUrls.add(S3ServiceDto.PartPresignedUrlResponse.builder()
                    .partNumber(i)
                    .presignedUrl("https://s3.amazonaws.com/presigned-url-" + i)
                    .contentLength(i == expectedPartCount ? fileSize - (partSize * (expectedPartCount - 1)) : partSize)
                    .build());
        }

        when(s3Service.generatePartPresignedUrls(any(S3ServiceDto.PartPresignedUrlRequest.class)))
                .thenReturn(partPresignedUrls);

        // When
        FileDto.MultipartUploadInitiateResponse response = fileService.initiateMultipartUpload(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getKey()).isEqualTo(expectedKey);
        assertThat(response.getUploadId()).isEqualTo(expectedUploadId);
        assertThat(response.getPartCount()).isEqualTo(expectedPartCount);
        assertThat(response.getPartPresignedUrls()).hasSize(expectedPartCount);

        // Verify
        verify(s3Service, times(1)).initiateUpload(any(S3ServiceDto.UploadInitiateRequest.class));
        verify(s3Service, times(1)).calculatePartCount(fileSize);
        verify(s3Service, times(1)).generatePartPresignedUrls(any(S3ServiceDto.PartPresignedUrlRequest.class));
    }

    @Test
    @DisplayName("멀티파트 업로드 완료 - 이미지 파일 (CONTENT, isThumbnail=false)")
    void testCompleteMultipartUpload_ImageContent() throws Exception {
        // Given
        String key = "files/uuid-123__test-image.jpg";
        String uploadId = "upload-id-123";
        String originalFileName = "test-image.jpg";
        Long fileSize = 1024 * 1024L;
        String mimeType = "image/jpeg";
        MediaRole mediaRole = MediaRole.CONTENT;

        // ETag가 포함된 Part 목록
        List<FileDto.Part> parts = new ArrayList<>();
        parts.add(FileDto.Part.builder()
                .partNumber(1)
                .etag("\"etag-123\"")
                .build());

        // S3Service Mock 설정
        // S3Response의 location은 실제 key를 포함한 S3 URL이어야 함
        String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        CompleteMultipartUploadResponse s3Response = CompleteMultipartUploadResponse.builder()
                .location(s3Url)
                .eTag("\"final-etag\"")
                .build();

        when(s3Service.completeUpload(any(S3ServiceDto.CompleteUploadRequest.class)))
                .thenReturn(s3Response);

        // FileRepository Mock 설정
        File savedFile = File.builder()
                .id(1L)
                .s3ObjectKey(key)
                .filename(originalFileName)
                .filePath(TEST_CDN_BASE_URL + "/" + key)
                .fileSize(fileSize)
                .mediaType(MediaType.IMAGE)
                .isThumbnail(false) // CONTENT이므로 false
                .build();

        when(fileRepository.save(any(File.class))).thenReturn(savedFile);

        // ArgumentCaptor로 저장되는 File 엔티티 캡처
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

        // When
        File result = fileService.completeMultipartUpload(
                key, uploadId, parts, originalFileName, fileSize, mimeType, mediaRole
        );

        // Then - 반환된 결과 검증
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getS3ObjectKey()).isEqualTo(key);
        assertThat(result.getFilename()).isEqualTo(originalFileName);
        assertThat(result.getFileSize()).isEqualTo(fileSize);
        assertThat(result.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(result.getIsThumbnail()).isFalse(); // CONTENT이므로 false

        // Then - DB에 저장되는 File 엔티티 검증
        verify(fileRepository, times(1)).save(fileCaptor.capture());
        File savedEntity = fileCaptor.getValue();

        assertThat(savedEntity.getS3ObjectKey()).isEqualTo(key);
        assertThat(savedEntity.getFilename()).isEqualTo(originalFileName);
        assertThat(savedEntity.getFileSize()).isEqualTo(fileSize);
        assertThat(savedEntity.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(savedEntity.getIsThumbnail()).isFalse(); // CONTENT이므로 false
        assertThat(savedEntity.getFilePath()).isNotNull();
        assertThat(savedEntity.getFilePath()).contains(TEST_CDN_BASE_URL);
        assertThat(savedEntity.getFilePath()).contains(key);

        // Verify
        verify(s3Service, times(1)).completeUpload(any(S3ServiceDto.CompleteUploadRequest.class));
    }

    @Test
    @DisplayName("멀티파트 업로드 완료 - 이미지 파일 (PREVIEW, isThumbnail=true)")
    void testCompleteMultipartUpload_ImagePreview() throws Exception {
        // Given
        String key = "files/uuid-456__preview-image.jpg";
        String uploadId = "upload-id-456";
        String originalFileName = "preview-image.jpg";
        Long fileSize = 512 * 1024L; // 512KB
        String mimeType = "image/jpeg";
        MediaRole mediaRole = MediaRole.PREVIEW; // PREVIEW는 isThumbnail = true

        List<FileDto.Part> parts = new ArrayList<>();
        parts.add(FileDto.Part.builder()
                .partNumber(1)
                .etag("\"etag-456\"")
                .build());

        // S3Service Mock 설정
        // S3Response의 location은 실제 key를 포함한 S3 URL이어야 함
        String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        CompleteMultipartUploadResponse s3Response = CompleteMultipartUploadResponse.builder()
                .location(s3Url)
                .eTag("\"final-etag\"")
                .build();

        when(s3Service.completeUpload(any(S3ServiceDto.CompleteUploadRequest.class)))
                .thenReturn(s3Response);

        // FileRepository Mock 설정
        File savedFile = File.builder()
                .id(2L)
                .s3ObjectKey(key)
                .filename(originalFileName)
                .filePath(TEST_CDN_BASE_URL + "/" + key)
                .fileSize(fileSize)
                .mediaType(MediaType.IMAGE)
                .isThumbnail(true) // PREVIEW이므로 true
                .build();

        when(fileRepository.save(any(File.class))).thenReturn(savedFile);

        // ArgumentCaptor로 저장되는 File 엔티티 캡처
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

        // When
        File result = fileService.completeMultipartUpload(
                key, uploadId, parts, originalFileName, fileSize, mimeType, mediaRole
        );

        // Then - 반환된 결과 검증
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getIsThumbnail()).isTrue(); // PREVIEW이므로 true

        // Then - DB에 저장되는 File 엔티티 검증
        verify(fileRepository, times(1)).save(fileCaptor.capture());
        File savedEntity = fileCaptor.getValue();

        assertThat(savedEntity.getS3ObjectKey()).isEqualTo(key);
        assertThat(savedEntity.getFilename()).isEqualTo(originalFileName);
        assertThat(savedEntity.getFileSize()).isEqualTo(fileSize);
        assertThat(savedEntity.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(savedEntity.getIsThumbnail()).isTrue(); // PREVIEW이므로 true
        assertThat(savedEntity.getFilePath()).isNotNull();
        assertThat(savedEntity.getFilePath()).contains(TEST_CDN_BASE_URL);
        assertThat(savedEntity.getFilePath()).contains(key);

        // Verify
        verify(s3Service, times(1)).completeUpload(any(S3ServiceDto.CompleteUploadRequest.class));
    }

    @Test
    @DisplayName("멀티파트 업로드 완료 - 여러 Part와 ETag 처리")
    void testCompleteMultipartUpload_MultiplePartsWithETags() throws Exception {
        // Given
        String key = "videos/uuid-789__test-video.mp4";
        String uploadId = "upload-id-789";
        String originalFileName = "test-video.mp4";
        Long fileSize = 200 * 1024 * 1024L; // 200MB
        String mimeType = "video/mp4";
        MediaRole mediaRole = MediaRole.CONTENT;

        // 여러 Part의 ETag 목록
        List<FileDto.Part> parts = new ArrayList<>();
        parts.add(FileDto.Part.builder().partNumber(1).etag("\"etag-part-1\"").build());
        parts.add(FileDto.Part.builder().partNumber(2).etag("\"etag-part-2\"").build());
        parts.add(FileDto.Part.builder().partNumber(3).etag("\"etag-part-3\"").build());

        // S3Service Mock 설정
        // S3Response의 location은 실제 key를 포함한 S3 URL이어야 함
        String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        CompleteMultipartUploadResponse s3Response = CompleteMultipartUploadResponse.builder()
                .location(s3Url)
                .eTag("\"final-etag-combined\"")
                .build();

        when(s3Service.completeUpload(any(S3ServiceDto.CompleteUploadRequest.class)))
                .thenReturn(s3Response);

        // FileRepository Mock 설정
        File savedFile = File.builder()
                .id(3L)
                .s3ObjectKey(key)
                .filename(originalFileName)
                .filePath(TEST_CDN_BASE_URL + "/" + key)
                .fileSize(fileSize)
                .mediaType(MediaType.VIDEO)
                .isThumbnail(false)
                .build();

        when(fileRepository.save(any(File.class))).thenReturn(savedFile);

        // ArgumentCaptor로 저장되는 File 엔티티 캡처
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

        // When
        File result = fileService.completeMultipartUpload(
                key, uploadId, parts, originalFileName, fileSize, mimeType, mediaRole
        );

        // Then - 반환된 결과 검증
        assertThat(result).isNotNull();
        assertThat(result.getMediaType()).isEqualTo(MediaType.VIDEO);

        // Then - DB에 저장되는 File 엔티티 검증
        verify(fileRepository, times(1)).save(fileCaptor.capture());
        File savedEntity = fileCaptor.getValue();

        assertThat(savedEntity.getS3ObjectKey()).isEqualTo(key);
        assertThat(savedEntity.getFilename()).isEqualTo(originalFileName);
        assertThat(savedEntity.getFileSize()).isEqualTo(fileSize);
        assertThat(savedEntity.getMediaType()).isEqualTo(MediaType.VIDEO);
        assertThat(savedEntity.getIsThumbnail()).isFalse();
        assertThat(savedEntity.getFilePath()).isNotNull();
        assertThat(savedEntity.getFilePath()).contains(TEST_CDN_BASE_URL);
        assertThat(savedEntity.getFilePath()).contains(key);

        // Verify - S3Service에 전달된 CompleteUploadRequest의 parts 검증
        verify(s3Service, times(1)).completeUpload(argThat(request -> {
            S3ServiceDto.CompleteUploadRequest req = (S3ServiceDto.CompleteUploadRequest) request;
            return req.getParts().size() == 3 &&
                    req.getParts().get(0).getPartNumber() == 1 &&
                    req.getParts().get(0).getEtag().equals("\"etag-part-1\"") &&
                    req.getParts().get(1).getPartNumber() == 2 &&
                    req.getParts().get(1).getEtag().equals("\"etag-part-2\"") &&
                    req.getParts().get(2).getPartNumber() == 3 &&
                    req.getParts().get(2).getEtag().equals("\"etag-part-3\"");
        }));
    }

    @Test
    @DisplayName("멀티파트 업로드 취소")
    void testAbortMultipartUpload() {
        // Given
        String key = "files/uuid-999__test-file.jpg";
        String uploadId = "upload-id-999";

        // When
        fileService.abortMultipartUpload(key, uploadId);

        // Then
        verify(s3Service, times(1)).abortUpload(argThat(request -> {
            S3ServiceDto.AbortUploadRequest req = (S3ServiceDto.AbortUploadRequest) request;
            return req.getKey().equals(key) && req.getUploadId().equals(uploadId);
        }));
    }

    @Test
    @DisplayName("썸네일 URL 생성 - CDN 사용")
    void testGetThumbnailUrl_WithCDN() {
        // Given
        File file = File.builder()
                .id(1L)
                .s3ObjectKey("files/uuid-123__test-image.jpg")
                .filename("test-image.jpg")
                .filePath(TEST_CDN_BASE_URL + "/files/uuid-123__test-image.jpg")
                .fileSize(1024 * 1024L)
                .mediaType(MediaType.IMAGE)
                .isThumbnail(false)
                .build();

        String size = "thumbnail";

        // When
        String thumbnailUrl = fileService.getThumbnailUrl(file, size);

        // Then
        assertThat(thumbnailUrl).isNotNull();
        assertThat(thumbnailUrl).contains("files/thumbnails/thumbnail/uuid-123__test-image.jpg");
        assertThat(thumbnailUrl).startsWith(TEST_CDN_BASE_URL);
    }

    @Test
    @DisplayName("썸네일 URL 생성 - CDN 없음 (예외 발생)")
    void testGetThumbnailUrl_WithoutCDN_ThrowsException() {
        // Given
        ReflectionTestUtils.setField(fileService, "cdnBaseUrl", null); // CDN 비활성화

        File file = File.builder()
                .id(1L)
                .s3ObjectKey("files/uuid-123__test-image.jpg")
                .filename("test-image.jpg")
                .filePath(TEST_S3_URL)
                .fileSize(1024 * 1024L)
                .mediaType(MediaType.IMAGE)
                .isThumbnail(false)
                .build();

        String size = "medium";

        // When & Then
        // CDN이 없으면 예외가 발생해야 함 (보안: 버킷명 노출 방지)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            fileService.getThumbnailUrl(file, size);
        });
    }

    @Test
    @DisplayName("썸네일 URL 생성 - 이미지가 아닌 경우 null 반환")
    void testGetThumbnailUrl_NonImage() {
        // Given
        File videoFile = File.builder()
                .id(2L)
                .s3ObjectKey("videos/uuid-456__test-video.mp4")
                .filename("test-video.mp4")
                .filePath(TEST_S3_URL)
                .fileSize(100 * 1024 * 1024L)
                .mediaType(MediaType.VIDEO) // 이미지가 아님
                .isThumbnail(false)
                .build();

        // When
        String thumbnailUrl = fileService.getThumbnailUrl(videoFile, "thumbnail");

        // Then
        assertThat(thumbnailUrl).isNull();
    }

    @Test
    @DisplayName("MediaType 결정 - 이미지")
    void testDetermineMediaType_Image() throws Exception {
        // Given
        String key = "files/uuid-123__test-image.jpg";
        String uploadId = "upload-id-123";
        String originalFileName = "test-image.jpg";
        Long fileSize = 1024 * 1024L;
        String mimeType = "image/jpeg";
        MediaRole mediaRole = MediaRole.CONTENT;

        List<FileDto.Part> parts = new ArrayList<>();
        parts.add(FileDto.Part.builder().partNumber(1).etag("\"etag-123\"").build());

        // S3Response의 location은 실제 key를 포함한 S3 URL이어야 함
        String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        CompleteMultipartUploadResponse s3Response = CompleteMultipartUploadResponse.builder()
                .location(s3Url)
                .eTag("\"final-etag\"")
                .build();

        when(s3Service.completeUpload(any())).thenReturn(s3Response);

        File savedFile = File.builder()
                .id(1L)
                .s3ObjectKey(key)
                .filename(originalFileName)
                .filePath(TEST_CDN_BASE_URL + "/" + key)
                .fileSize(fileSize)
                .mediaType(MediaType.IMAGE)
                .isThumbnail(false)
                .build();

        when(fileRepository.save(any(File.class))).thenReturn(savedFile);

        // ArgumentCaptor로 저장되는 File 엔티티 캡처
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

        // When
        File result = fileService.completeMultipartUpload(
                key, uploadId, parts, originalFileName, fileSize, mimeType, mediaRole
        );

        // Then - 반환된 결과 검증
        assertThat(result.getMediaType()).isEqualTo(MediaType.IMAGE);

        // Then - DB에 저장되는 File 엔티티 검증
        verify(fileRepository, times(1)).save(fileCaptor.capture());
        File savedEntity = fileCaptor.getValue();
        assertThat(savedEntity.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(savedEntity.getS3ObjectKey()).isEqualTo(key);
        assertThat(savedEntity.getFilename()).isEqualTo(originalFileName);
        assertThat(savedEntity.getFileSize()).isEqualTo(fileSize);
    }

    @Test
    @DisplayName("MediaType 결정 - 동영상")
    void testDetermineMediaType_Video() throws Exception {
        // Given
        String key = "videos/uuid-456__test-video.mp4";
        String uploadId = "upload-id-456";
        String originalFileName = "test-video.mp4";
        Long fileSize = 100 * 1024 * 1024L;
        String mimeType = "video/mp4";
        MediaRole mediaRole = MediaRole.CONTENT;

        List<FileDto.Part> parts = new ArrayList<>();
        parts.add(FileDto.Part.builder().partNumber(1).etag("\"etag-456\"").build());

        // S3Response의 location은 실제 key를 포함한 S3 URL이어야 함
        String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        CompleteMultipartUploadResponse s3Response = CompleteMultipartUploadResponse.builder()
                .location(s3Url)
                .eTag("\"final-etag\"")
                .build();

        when(s3Service.completeUpload(any())).thenReturn(s3Response);

        File savedFile = File.builder()
                .id(2L)
                .s3ObjectKey(key)
                .filename(originalFileName)
                .filePath(TEST_CDN_BASE_URL + "/" + key)
                .fileSize(fileSize)
                .mediaType(MediaType.VIDEO)
                .isThumbnail(false)
                .build();

        when(fileRepository.save(any(File.class))).thenReturn(savedFile);

        // ArgumentCaptor로 저장되는 File 엔티티 캡처
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

        // When
        File result = fileService.completeMultipartUpload(
                key, uploadId, parts, originalFileName, fileSize, mimeType, mediaRole
        );

        // Then - 반환된 결과 검증
        assertThat(result.getMediaType()).isEqualTo(MediaType.VIDEO);

        // Then - DB에 저장되는 File 엔티티 검증
        verify(fileRepository, times(1)).save(fileCaptor.capture());
        File savedEntity = fileCaptor.getValue();
        assertThat(savedEntity.getMediaType()).isEqualTo(MediaType.VIDEO);
        assertThat(savedEntity.getS3ObjectKey()).isEqualTo(key);
        assertThat(savedEntity.getFilename()).isEqualTo(originalFileName);
        assertThat(savedEntity.getFileSize()).isEqualTo(fileSize);
    }

    @Test
    @DisplayName("MediaType 결정 - 오디오")
    void testDetermineMediaType_Audio() throws Exception {
        // Given
        String key = "files/uuid-789__test-audio.mp3";
        String uploadId = "upload-id-789";
        String originalFileName = "test-audio.mp3";
        Long fileSize = 5 * 1024 * 1024L;
        String mimeType = "audio/mpeg";
        MediaRole mediaRole = MediaRole.CONTENT;

        List<FileDto.Part> parts = new ArrayList<>();
        parts.add(FileDto.Part.builder().partNumber(1).etag("\"etag-789\"").build());

        // S3Response의 location은 실제 key를 포함한 S3 URL이어야 함
        String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        CompleteMultipartUploadResponse s3Response = CompleteMultipartUploadResponse.builder()
                .location(s3Url)
                .eTag("\"final-etag\"")
                .build();

        when(s3Service.completeUpload(any())).thenReturn(s3Response);

        File savedFile = File.builder()
                .id(3L)
                .s3ObjectKey(key)
                .filename(originalFileName)
                .filePath(TEST_CDN_BASE_URL + "/" + key)
                .fileSize(fileSize)
                .mediaType(MediaType.MUSIC)
                .isThumbnail(false)
                .build();

        when(fileRepository.save(any(File.class))).thenReturn(savedFile);

        // ArgumentCaptor로 저장되는 File 엔티티 캡처
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

        // When
        File result = fileService.completeMultipartUpload(
                key, uploadId, parts, originalFileName, fileSize, mimeType, mediaRole
        );

        // Then - 반환된 결과 검증
        assertThat(result.getMediaType()).isEqualTo(MediaType.MUSIC);

        // Then - DB에 저장되는 File 엔티티 검증
        verify(fileRepository, times(1)).save(fileCaptor.capture());
        File savedEntity = fileCaptor.getValue();
        assertThat(savedEntity.getMediaType()).isEqualTo(MediaType.MUSIC);
        assertThat(savedEntity.getS3ObjectKey()).isEqualTo(key);
        assertThat(savedEntity.getFilename()).isEqualTo(originalFileName);
        assertThat(savedEntity.getFileSize()).isEqualTo(fileSize);
    }

    @Test
    @DisplayName("CDN URL 생성 - CDN base URL 사용")
    void testGenerateCdnUrl_WithCDN() throws Exception {
        // Given
        String key = "files/uuid-123__test-image.jpg";
        String uploadId = "upload-id-123";
        String originalFileName = "test-image.jpg";
        Long fileSize = 1024 * 1024L;
        String mimeType = "image/jpeg";
        MediaRole mediaRole = MediaRole.CONTENT;

        List<FileDto.Part> parts = new ArrayList<>();
        parts.add(FileDto.Part.builder().partNumber(1).etag("\"etag-123\"").build());

        // S3 URL에서 key 추출하여 CDN URL로 변환
        // S3Response의 location은 실제 key를 포함한 S3 URL이어야 함
        String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        CompleteMultipartUploadResponse s3Response = CompleteMultipartUploadResponse.builder()
                .location(s3Url)
                .eTag("\"final-etag\"")
                .build();

        when(s3Service.completeUpload(any())).thenReturn(s3Response);

        File savedFile = File.builder()
                .id(1L)
                .s3ObjectKey(key)
                .filename(originalFileName)
                .filePath(TEST_CDN_BASE_URL + "/files/uuid-123__test-image.jpg")
                .fileSize(fileSize)
                .mediaType(MediaType.IMAGE)
                .isThumbnail(false)
                .build();

        when(fileRepository.save(any(File.class))).thenReturn(savedFile);

        // ArgumentCaptor로 저장되는 File 엔티티 캡처
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

        // When
        File result = fileService.completeMultipartUpload(
                key, uploadId, parts, originalFileName, fileSize, mimeType, mediaRole
        );

        // Then - 반환된 결과 검증
        assertThat(result.getFilePath()).isNotNull();
        assertThat(result.getFilePath()).contains(TEST_CDN_BASE_URL);
        assertThat(result.getFilePath()).contains("files/uuid-123__test-image.jpg");

        // Then - DB에 저장되는 File 엔티티의 filePath 검증
        verify(fileRepository, times(1)).save(fileCaptor.capture());
        File savedEntity = fileCaptor.getValue();
        assertThat(savedEntity.getFilePath()).isNotNull();
        assertThat(savedEntity.getFilePath()).contains(TEST_CDN_BASE_URL);
        assertThat(savedEntity.getFilePath()).contains("files/uuid-123__test-image.jpg");
        assertThat(savedEntity.getS3ObjectKey()).isEqualTo(key);
        assertThat(savedEntity.getFilename()).isEqualTo(originalFileName);
        assertThat(savedEntity.getFileSize()).isEqualTo(fileSize);
    }

    @Test
    @DisplayName("CDN URL 생성 - CDN 없음 (예외 발생)")
    void testGenerateCdnUrl_WithoutCDN_ThrowsException() throws Exception {
        // Given
        ReflectionTestUtils.setField(fileService, "cdnBaseUrl", null); // CDN 비활성화

        String key = "files/uuid-123__test-image.jpg";
        String uploadId = "upload-id-123";
        String originalFileName = "test-image.jpg";
        Long fileSize = 1024 * 1024L;
        String mimeType = "image/jpeg";
        MediaRole mediaRole = MediaRole.CONTENT;

        List<FileDto.Part> parts = new ArrayList<>();
        parts.add(FileDto.Part.builder().partNumber(1).etag("\"etag-123\"").build());

        String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/files/uuid-123__test-image.jpg";
        CompleteMultipartUploadResponse s3Response = CompleteMultipartUploadResponse.builder()
                .location(s3Url)
                .eTag("\"final-etag\"")
                .build();

        when(s3Service.completeUpload(any())).thenReturn(s3Response);

        // When & Then
        // CDN이 없으면 예외가 발생해야 함 (보안: 버킷명 노출 방지)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            fileService.completeMultipartUpload(
                    key, uploadId, parts, originalFileName, fileSize, mimeType, mediaRole
            );
        });
    }
}
