package com.depth.deokive.domain.file.controller;

import com.depth.deokive.domain.file.dto.FileDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.system.ratelimit.annotation.RateLimit;
import com.depth.deokive.system.ratelimit.annotation.RateLimitType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/files")
@Tag(name = "File", description = "파일 업로드(S3 Multipart) 관리 API")
public class FileController {

    private final FileService fileService;

    /**
     * 1. 멀티파트 업로드 초기화
     * 프론트엔드에서 파일 메타데이터를 보내면, S3 Upload ID와 Presigned URL들을 발급합니다.
     */
    @PostMapping("/multipart/initiate")
    @RateLimit(type = RateLimitType.USER, capacity = 50, refillTokens = 50, refillPeriodSeconds = 3600, failClosed = true)
    @Operation(summary = "멀티파트 업로드 초기화", description = "파일 크기에 따른 Part 계산 및 Presigned URL 발급")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "초기화 성공 (Upload ID 발급)"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (파일 이름 누락, 지원하지 않는 형식 등)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "S3 연동 실패 (서버 내부 오류)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FileDto.MultipartUploadInitiateResponse> initiateMultipartUpload(
            @Valid @RequestBody FileDto.MultipartUploadInitiateRequest request
    ) {
        return ResponseEntity.ok(fileService.initiateMultipartUpload(request));
    }

    /**
     * 2. 멀티파트 업로드 완료
     * S3에 모든 Part 업로드가 끝난 후 호출. 서버가 S3에 병합 요청을 보내고 DB에 파일 정보를 저장합니다.
     */
    @PostMapping("/multipart/complete")
    @RateLimit(type = RateLimitType.USER, capacity = 50, refillTokens = 50, refillPeriodSeconds = 3600, failClosed = true)
    @Operation(summary = "멀티파트 업로드 완료", description = "S3 병합 요청 및 DB 메타데이터 저장")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "업로드 완료 및 메타데이터 저장 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (Part 정보 불일치, ETag 오류)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "파일 저장 실패 (DB 또는 S3 오류)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FileDto.UploadFileResponse> completeMultipartUpload(
            @Valid @RequestBody FileDto.CompleteMultipartUploadRequest request
    ) {
        // 1. 서비스 로직 호출 (S3 병합 + DB 저장)
        File savedFile = fileService.completeMultipartUpload(request);

        // 2. Entity -> Response DTO 변환
        FileDto.UploadFileResponse response =
                FileDto.UploadFileResponse.of(savedFile, request);

        return ResponseEntity.ok(response);
    }

    /**
     * 3. 멀티파트 업로드 취소
     * 업로드 중 사용자가 취소하거나 실패했을 때, S3에 남아있는 조각(Parts)들을 정리합니다.
     */
    @PostMapping("/multipart/abort")
    @RateLimit(type = RateLimitType.USER, capacity = 100, refillTokens = 100, refillPeriodSeconds = 3600)
    @Operation(summary = "멀티파트 업로드 취소", description = "업로드 중단 시 S3 잔여 조각 데이터 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "업로드 취소 성공"),
            @ApiResponse(responseCode = "500", description = "S3 연동 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> abortMultipartUpload(
            @RequestBody FileDto.MultipartUploadAbortRequest request
    ) {
        fileService.abortMultipartUpload(request.getKey(), request.getUploadId());
        return ResponseEntity.ok().build();
    }
}