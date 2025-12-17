package com.depth.deokive.domain.gallery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GalleryResponseDto {
    private Long galleryId;
    private String title;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
}
