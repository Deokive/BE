package com.depth.deokive.domain.gallery.controller;

import com.depth.deokive.domain.gallery.dto.GalleryDto;
import com.depth.deokive.domain.gallery.service.GalleryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/gallery")
@Tag(name = "Gallery", description = "갤러리 API")
public class GalleryController {

    private final GalleryService galleryService;


    @GetMapping("/{archiveId}")
    @Operation(summary = "갤러리 목록 조회", description = "특정 아카이브의 갤러리 이미지들을 페이징하여 조회합니다.")
    @ApiResponse(responseCode = "200", description = "갤러리 목록 조회 성공")
    public ResponseEntity<GalleryDto.PageListResponse> getGalleries(
            @PathVariable Long archiveId,
            // @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
            @Valid @ModelAttribute GalleryDto.GalleryPageRequest pageRequest
    ) {
        GalleryDto.PageListResponse response = galleryService.getGalleries(archiveId, pageRequest.toPageable());
        return ResponseEntity.ok(response);
    }
}