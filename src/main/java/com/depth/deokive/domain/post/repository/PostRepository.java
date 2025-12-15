package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}

