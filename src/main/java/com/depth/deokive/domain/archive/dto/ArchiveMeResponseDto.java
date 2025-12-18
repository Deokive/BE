package com.depth.deokive.domain.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ArchiveMeResponseDto {
    private Long id;
    private String title;
    private String thumbnailUrl; // 썸네일 이미지 URL
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
}
