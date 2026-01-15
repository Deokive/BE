package com.depth.deokive.domain.post.entity;

import com.depth.deokive.domain.post.entity.enums.Category;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "post_stats",
        indexes = {
                // 카테고리 필터링 O + 정렬 (커버링 인덱스)
                @Index(name = "idx_stats_cat_hot", columnList = "category, hot_score DESC, post_id DESC"),
                @Index(name = "idx_stats_cat_new", columnList = "category, created_at DESC, post_id DESC"),
                @Index(name = "idx_stats_cat_view", columnList = "category, view_count DESC, post_id DESC"),

                // 전체 조회 + 정렬 (커버링 인덱스)
                @Index(name = "idx_stats_hot", columnList = "hot_score DESC, post_id DESC"),
                @Index(name = "idx_stats_new", columnList = "created_at DESC, post_id DESC"),
                @Index(name = "idx_stats_view", columnList = "view_count DESC, post_id DESC")
        }
)
public class PostStats {

    @Id
    @Column(name = "post_id")
    private Long id; // Post ID와 동일한 값

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long likeCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Double hotScore = 0.0;

    // NOTICE: Post의 카테고리가 변경되면 여기도 같이 변경해줘야 함 (Service 레벨 처리)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static PostStats create(Post post) {
        return PostStats.builder()
                .post(post)
                .category(post.getCategory()) // Post의 카테고리 복제
                .createdAt(post.getCreatedAt() != null ? post.getCreatedAt() : LocalDateTime.now()) // 생성일 복제
                .viewCount(0L)
                .likeCount(0L)
                .hotScore(0.0)
                .build();
    }

    public void syncCategory(Category newCategory) { this.category = newCategory; }
}