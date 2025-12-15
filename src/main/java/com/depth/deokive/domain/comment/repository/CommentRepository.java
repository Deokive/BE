package com.depth.deokive.domain.comment.repository;

import com.depth.deokive.domain.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
}

