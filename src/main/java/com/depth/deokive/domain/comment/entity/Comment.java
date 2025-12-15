package com.depth.deokive.domain.comment.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(
    name = "comment",
    uniqueConstraints = {
        // 게시글 내 댓글 조회 + 정렬 + 중복 방지
        @UniqueConstraint(
            name = "idx_comment_post_path",
            columnNames = {"post_id", "path"}
        )
    },
    // 마이페이지 (내가 쓴 댓글) 조회용 (확장 가능성 버전)
    indexes = {
        @Index(name = "idx_comment_user", columnList = "user_id")
    }
)
public class Comment extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 3000)
    private String content;

    @Column(nullable = false, length = 15)
    private String path; // comment depth

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
