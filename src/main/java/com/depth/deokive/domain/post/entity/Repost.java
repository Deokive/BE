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
        @Index(name = "idx_repost_tab_created", columnList = "repost_tab_id, created_at DESC")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_repost_tab_post", columnNames = {"repost_tab_id", "post_id"})
    }
)
public class Repost extends TimeBaseEntity {
    @Id @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    // Post가 삭제되어도 이 값은 보존됨 -> 404 링크 유도 (UX 고려: post 삭제 시 해당 유저의 repost가 영문도 모른채 사라지는걸 방지)
    @Column(name = "post_id", nullable = false) // loose coupling
    private Long postId;

    @Column(nullable = false)
    private String title; // Default: 게시글 제목 -> Snapshot (삭제되면 못가져오니까. 그리고 이름 수정도 가능하라고)

    @Column(name = "thumbnail_key")
    private String thumbnailKey; // 원본 썸네일 URL (조회용) -> Snapshot

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repost_tab_id", nullable = false)
    private RepostTab repostTab;

    public void updateTitle(String title) {
        this.title = title;
    }
}