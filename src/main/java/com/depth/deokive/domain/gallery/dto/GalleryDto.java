package com.depth.deokive.domain.gallery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class GalleryDto {
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long id; // 갤러리 삭제하거나 수정하고 싶을 수 있으니까
        private String thumbnailUrl;
        private LocalDateTime createdAt;
        private LocalDateTime lastModifiedAt;
    }
}
