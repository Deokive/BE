package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.RepostTab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepostTabRepository extends JpaRepository<RepostTab, Long> {
    long countByRepostBookId(Long repostBookId);

    @Modifying
    @Query("DELETE FROM RepostTab rt WHERE rt.repostBook.id = :bookId")
    void deleteByBookId(@Param("bookId") Long bookId);

    // 탭 ID순으로 가져오기
    List<RepostTab> findAllByRepostBookIdOrderByIdAsc(Long repostBookId);

    /**
     * Repost 생성 시 필요한 검증 데이터를 Fetch Join으로 한 번에 로딩
     * - RepostTab → RepostBook → Archive → User (소유권 검증용)
     * - N+1 방지 + Lazy Loading 최소화
     */
    @Query("""
        SELECT rt FROM RepostTab rt
        JOIN FETCH rt.repostBook rb
        JOIN FETCH rb.archive a
        JOIN FETCH a.user u
        WHERE rt.id = :tabId
        """)
    Optional<RepostTab> findByIdWithOwner(@Param("tabId") Long tabId);
}