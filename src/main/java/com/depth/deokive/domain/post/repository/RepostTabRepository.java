package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.RepostTab;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepostTabRepository extends JpaRepository<RepostTab, Long> {
    long countByRepostBookId(Long repostBookId);
}