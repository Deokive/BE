package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.PostLikeCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeCountRepository extends JpaRepository<PostLikeCount, Long> {
}

