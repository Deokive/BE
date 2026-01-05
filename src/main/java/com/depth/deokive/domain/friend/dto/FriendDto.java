package com.depth.deokive.domain.friend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.List;

public class FriendDto {

    @Data @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "친구 프로필 정보")
    public static class Response {
        @Schema(description = "유저 ID", example = "1")
        private Long userId;

        @Schema(description = "닉네임", example = "덕후123")
        private String nickname;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "친구 목록 응답")
    public static class FriendListResponse {
        @Schema(description = "친구 데이터 목록")
        private List<Response> content;

        @Schema(description = "페이지 메타데이터")
        private PageInfo page;

        public static FriendListResponse of(Slice<Response> sliceData) {
            return FriendListResponse.builder()
                    .content(sliceData.getContent())
                    .page(new PageInfo(sliceData))
                    .build();
        }
    }

    @Data @AllArgsConstructor
    public static class PageInfo {
        private int size;
        private int pageNumber;
        private boolean hasNext;
        private boolean hasPrev;

        public PageInfo(Slice<?> slice) {
            this.size = slice.getSize();
            this.pageNumber = slice.getNumber();
            this.hasNext = slice.hasNext();
            this.hasPrev = slice.hasPrevious();
        }
    }
}