package com.depth.deokive.domain.post.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(
    name = "post",
    indexes = {
        // 1. 카테고리별 최신순/인기순 조회 최적화
        @Index(name = "idx_post_cat_hot", columnList = "category, hot_score DESC"),
        @Index(name = "idx_post_cat_new", columnList = "category, created_at DESC"),

        // 2. 마이페이지용 (내 글 조회)
        @Index(name = "idx_post_user_new", columnList = "user_id, created_at DESC")
    }
)
public class Post extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false, length = 5000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Denormalization Fields for Pagination Performance
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_file_id")
    private File thumbnailFile; // 리스트 조회 시 조인 비용 제거를 위한 썸네일 직접 참조

    @Builder.Default
    @Column(nullable = false)
    private Long viewCount = 0L;

    @Builder.Default
    @Column(nullable = false)
    private Long likeCount = 0L;

    @Builder.Default
    @Column(nullable = false)
    private Double hotScore = 0.0;

    public void update(PostDto.Request request) {
        if (request == null) return;

        this.title = nonBlankOrDefault(request.getTitle(), this.title);
        this.category = nonBlankOrDefault(request.getCategory(), this.category);
        this.content = nonBlankOrDefault(request.getContent(), this.content);
    }

    // TODO: 서비스 로직 Update 로직 점검
    public void updateThumbnail(File file) { this.thumbnailFile = file; }
    public void increaseViewCount() { this.viewCount++; }

    // TODO: 사실 이게 PATCH 패턴 처리 방식. Validation 에서 체크를 해줘서 빈 값 들어올 일은 없긴 한데... 일단 보류. 리팩토링 단계에서 고려
    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}