package com.depth.deokive.domain.post.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
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
        @Index(name = "idx_repost_url", columnList = "url(255)")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_repost_tab_url", columnNames = {"repost_tab_id", "url"})
    }
)
public class Repost extends TimeBaseEntity {
    @Id @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    // 외부 SNS URL (Twitter, Instagram 등)
    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private String title; // OG 메타데이터에서 추출된 제목 (수정 가능)

    @Column(name = "thumbnail_url", length = 2048)
    private String thumbnailUrl; // OG 추출된 썸네일 URL (nullable)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repost_tab_id", nullable = false)
    private RepostTab repostTab;

    public void updateTitle(String title) {
        this.title = title;
    }
}