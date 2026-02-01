package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.Repost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepostRepository extends JpaRepository<Repost, Long> {
    boolean existsByRepostTabIdAndUrl(Long repostTabId, String url);

    // Bulk Delete -> 성능 최적화
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Repost r WHERE r.repostTab.id = :tabId")
    void deleteAllByRepostTabId(@Param("tabId") Long tabId);

    // Repost -> Tab -> Book 구조. BookId로 하위 Repost 일괄 삭제
    @Modifying
    @Query("DELETE FROM Repost r WHERE r.repostTab.id IN (SELECT rt.id FROM RepostTab rt WHERE rt.repostBook.id = :bookId)")
    void deleteByBookId(@Param("bookId") Long bookId);
}