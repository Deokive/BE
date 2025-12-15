package com.depth.deokive.domain.comment.entity;

import com.depth.deokive.domain.post.entity.Post;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "comment_count")
public class CommentCount {
    @Id
    @Column(name = "post_id")
    private Long postId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Builder.Default
    @Column(nullable = false)
    private long count = 0;

    public void increase() { this.count++; }

    public void decrease() {
        if (this.count > 0) { this.count--; }
    }
}
