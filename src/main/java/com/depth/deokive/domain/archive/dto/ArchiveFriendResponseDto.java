package com.depth.deokive.domain.archive.dto;

import com.depth.deokive.domain.archive.entity.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ArchiveFriendResponseDto {
    private Long id;
    private Visibility visibility; // 공개 범위 (PUBLIC, RESTRICTED)
    private String title;
    private String thumbnailUrl; // 썸네일 URL
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
}
