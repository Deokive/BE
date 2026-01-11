package com.depth.deokive.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "post_like_count")
public class PostLikeCount {

    @Id @Column(name = "post_id")
    private Long postId; // Post ID와 동일 (Shared PK)

    @Column(nullable = false)
    private Long count;

    public static PostLikeCount create(Long postId) {
        return PostLikeCount.builder()
                .postId(postId)
                .count(0L)
                .build();
    }

}