package com.depth.deokive.domain.post.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.post.entity.enums.RepostStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(
    name = "repost",
    indexes = {
        @Index(name = "idx_repost_tab_created", columnList = "repost_tab_id, created_at DESC, id DESC"),
        @Index(name = "idx_repost_url_hash", columnList = "url_hash")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_repost_tab_url_hash", columnNames = {"repost_tab_id", "url_hash"})
    }
)
public class Repost extends TimeBaseEntity {
    @Id @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    // 외부 SNS URL (Twitter, Instagram 등)
    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    // URL 해시값 (SHA-256, 고정 길이 64자) - Unique constraint용
    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(nullable = false)
    private String title; // OG 메타데이터에서 추출된 제목 (수정 가능)

    @Column(name = "thumbnail_url", length = 2048)
    private String thumbnailUrl; // OG 추출된 썸네일 URL (nullable)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RepostStatus status; // OG 메타데이터 추출 상태

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repost_tab_id", nullable = false)
    private RepostTab repostTab;

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public void updateMetadata(String title, String thumbnailUrl) {
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void completeMetadata(String title, String thumbnailUrl) {
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.status = RepostStatus.COMPLETED;
    }

    public void markAsFailed() {
        this.status = RepostStatus.FAILED;
    }
}