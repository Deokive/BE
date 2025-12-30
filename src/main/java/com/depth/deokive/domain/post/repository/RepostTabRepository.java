package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.RepostTab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RepostTabRepository extends JpaRepository<RepostTab, Long> {
    long countByRepostBookId(Long repostBookId);

    @Modifying
    @Query("DELETE FROM RepostTab rt WHERE rt.repostBook.id = :bookId")
    void deleteByBookId(@Param("bookId") Long bookId);

    // 탭 ID순으로 가져오기
    List<RepostTab> findAllByRepostBookIdOrderByIdAsc(Long repostBookId);
}