package com.depth.deokive.domain.gallery.controller;

import com.depth.deokive.domain.gallery.dto.GalleryDto;
import com.depth.deokive.domain.gallery.service.GalleryService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/gallery")
@Tag(name = "Gallery", description = "갤러리 API")
public class GalleryController {

    private final GalleryService galleryService;

    @GetMapping("/{archiveId}")
    @Operation(summary = "갤러리 목록 조회", description = "특정 아카이브의 갤러리 이미지들을 페이징하여 조회합니다.")
    @ApiResponse(responseCode = "200", description = "갤러리 목록 조회 성공", content = @Content(
         mediaType = "application/json",
         schema = @Schema(implementation = GalleryDto.PageListResponse.class)))
    public ResponseEntity<GalleryDto.PageListResponse> getGalleries(
            @PathVariable Long archiveId,
            // @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
            @Valid @ModelAttribute GalleryDto.GalleryPageRequest pageRequest
    ) {
        GalleryDto.PageListResponse response = galleryService.getGalleries(archiveId, pageRequest.toPageable());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{archiveId}")
    @Operation(summary = "갤러리 이미지 등록")
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
    public ResponseEntity<Void> deleteGalleries(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @RequestBody GalleryDto.DeleteRequest request
    ) {
        galleryService.deleteGalleries(userPrincipal, archiveId, request);
        return ResponseEntity.noContent().build();
    }
}