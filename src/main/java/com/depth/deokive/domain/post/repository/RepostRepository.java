package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.Repost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepostRepository extends JpaRepository<Repost, Long> {
    boolean existsByRepostTabIdAndPostId(Long repostTabId, Long postId);

    // Bulk Delete -> 성능 최적화
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Repost r WHERE r.repostTab.id = :tabId")
    void deleteAllByRepostTabId(@Param("tabId") Long tabId);
}