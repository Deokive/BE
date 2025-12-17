package com.depth.deokive.domain.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ArchiveResponseDto {
    private Long archiveId;
    private String title;
    private String nickname; // 작성자 닉네임
    private String thumbnailUrl; // 썸네일 이미지 URL
    private long likeCount;
    private long viewCount;
    private boolean isLiked; // 내가 좋아요 눌렀는지 여부
    private LocalDateTime createdAt;
}
