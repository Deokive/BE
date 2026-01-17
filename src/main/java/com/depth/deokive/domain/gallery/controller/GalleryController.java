package com.depth.deokive.domain.gallery.controller;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.domain.gallery.dto.GalleryDto;
import com.depth.deokive.domain.gallery.service.GalleryService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/gallery")
@Tag(name = "Gallery", description = "갤러리 API")
public class GalleryController {

    private final GalleryService galleryService;

    @GetMapping("/{archiveId}")
    @Operation(summary = "갤러리 목록 조회", description = "특정 아카이브의 갤러리 이미지들을 페이징하여 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "갤러리 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 요청 (범위 초과)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 아카이브)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageDto.PageListResponse<GalleryDto.Response>> getGalleries(
            @PathVariable Long archiveId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @ModelAttribute GalleryDto.GalleryPageRequest pageRequest
    ) {
        return ResponseEntity.ok(galleryService.getGalleries(userPrincipal, archiveId, pageRequest.toPageable()));
    }

    @PostMapping("/{archiveId}")
    @Operation(summary = "갤러리 이미지 등록")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "갤러리 이미지 등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "등록 권한 없음 (아카이브 소유자가 아님) 또는 파일 접근 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브 또는 파일입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GalleryDto.CreateResponse> createGalleries(
          @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
          @PathVariable Long archiveId,
          @Valid @RequestBody GalleryDto.CreateRequest request
    ) {
        GalleryDto.CreateResponse response = galleryService.createGalleries(userPrincipal, archiveId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{archiveId}")
    @Operation(summary = "갤러리북 제목 수정")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "갤러리북 제목 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (제목 누락 등)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (아카이브 소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GalleryDto.UpdateTitleResponse> updateGalleryBookTitle(
          @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
          @PathVariable Long archiveId,
          @Valid @RequestBody GalleryDto.UpdateTitleRequest request
    ) {
        GalleryDto.UpdateTitleResponse response = galleryService.updateGalleryBookTitle(userPrincipal, archiveId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{archiveId}")
    @Operation(summary = "갤러리 이미지 삭제", description = "선택한 갤러리 이미지들을 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "갤러리 이미지 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (아카이브 소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteGalleries(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @RequestBody GalleryDto.DeleteRequest request
    ) {
        galleryService.deleteGalleries(userPrincipal, archiveId, request);
        return ResponseEntity.noContent().build();
    }
}