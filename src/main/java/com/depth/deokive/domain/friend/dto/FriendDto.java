package com.depth.deokive.domain.friend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.util.List;

public class FriendDto {

    @Data @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "친구 프로필 정보")
    public static class Response {
        @Schema(description = "유저 ID", example = "1")
        private Long userId;

        @Schema(description = "닉네임", example = "덕후123")
        private String nickname;

        @Schema(description = "친구 수락 시간", example = "2026-01-06T12:00:00")
        private LocalDateTime acceptedAt;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "친구 목록 응답")
    public static class FriendListResponse {
        @Schema(description = "친구 데이터 목록")
        private List<Response> content;

        @Schema(description = "다음 페이지 존재 여부")
        private boolean hasNext;

        @Schema(description = "요청한 페이지 사이즈")
        private int pageSize;

        public static FriendListResponse of(Slice<Response> sliceData) {
            return FriendListResponse.builder()
                    .content(sliceData.getContent())
                    .hasNext(sliceData.hasNext())
                    .pageSize(sliceData.getSize())
                    .build();
        }
    }
}