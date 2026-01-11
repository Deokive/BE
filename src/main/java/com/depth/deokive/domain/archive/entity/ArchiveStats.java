package com.depth.deokive.domain.archive.entity;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "archive_stats",
        indexes = {
                @Index(name = "idx_stats_vis_hot", columnList = "visibility, hot_score DESC, archive_id DESC"),
                @Index(name = "idx_stats_vis_new", columnList = "visibility, created_at DESC, archive_id DESC"),
                @Index(name = "idx_stats_vis_view", columnList = "visibility, view_count DESC, archive_id DESC"),
                @Index(name = "idx_stats_vis_like", columnList = "visibility, like_count DESC, archive_id DESC"),
        }
)
public class ArchiveStats {

    @Id
    @Column(name = "archive_id")
    private Long id; // Archive ID와 동일 (Shared PK)

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archive_id")
    private Archive archive;

    @Column(nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long likeCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Double hotScore = 0.0;

    // --- 반정규화 데이터 (조회 성능 최적화용) ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Badge badge;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // --- Factory Method ---
    public static ArchiveStats create(Archive archive) {
        return ArchiveStats.builder()
                .archive(archive)
                .viewCount(0L)
                .likeCount(0L)
                .hotScore(0.0)
                .visibility(archive.getVisibility()) // 초기값 복제
                .badge(archive.getBadge())           // 초기값 복제
                .createdAt(archive.getCreatedAt() != null ? archive.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    // --- Sync Methods (Archive 수정 시 호출) ---
    public void syncVisibility(Visibility visibility) { this.visibility = visibility; }
    public void syncBadge(Badge badge) { this.badge = badge; }
}