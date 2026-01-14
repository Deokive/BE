package com.depth.deokive.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class LikeMessageDto {
    private Long id; // postId, ArchiveId
    private Long userId;
    private boolean isLiked; // true: 좋아요 추가, false: 좋아요 취소
}