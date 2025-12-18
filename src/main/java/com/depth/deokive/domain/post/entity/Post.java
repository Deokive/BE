package com.depth.deokive.domain.post.entity;

import com.depth.deokive.common.auditor.UserBaseEntity;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.enums.Category;
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
@Table(name = "post")
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

    public void update(PostDto.Request request) {
        if (request == null) return;

        this.title = nonBlankOrDefault(request.getTitle(), this.title);
        this.category = nonBlankOrDefault(request.getCategory(), this.category);
        this.content = nonBlankOrDefault(request.getContent(), this.content);
    }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}