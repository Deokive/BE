package com.depth.deokive.domain.comment.repository;

import com.depth.deokive.domain.comment.entity.CommentCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentCountRepository extends JpaRepository<CommentCount, Long> {
}

