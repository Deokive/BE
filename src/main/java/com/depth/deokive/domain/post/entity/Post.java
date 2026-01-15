package com.depth.deokive.domain.post.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.common.auditor.UserBaseEntity;
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
        // 마이페이지용 (내 글 조회)
        @Index(name = "idx_post_user_new", columnList = "user_id, created_at DESC, id DESC")
    }
)
public class Post extends UserBaseEntity {
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

    @Column(name = "thumbnail_key")
    private String thumbnailKey; // Denormalization Fields for Pagination Performance

    public void update(PostDto.UpdateRequest request) {
        if (request == null) return;

        this.title = nonBlankOrDefault(request.getTitle(), this.title);
        this.category = nonBlankOrDefault(request.getCategory(), this.category); // postStats의 category도 변경해줘야 함
        this.content = nonBlankOrDefault(request.getContent(), this.content);
    }

    public void updateThumbnail(String thumbnailKey) { this.thumbnailKey = thumbnailKey; }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}