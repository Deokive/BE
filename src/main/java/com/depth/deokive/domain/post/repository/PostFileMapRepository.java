package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.PostFileMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostFileMapRepository extends JpaRepository<PostFileMap, Long> {
    List<PostFileMap> findAllByPostIdOrderBySequenceAsc(Long postId); // 게시글 ID로 파일 매핑 조회 (순서대로)

    @Modifying
    @Query("DELETE FROM PostFileMap pfm WHERE pfm.post.id = :postId") // 게시글의 모든 파일 연결 삭제 (수정/삭제 시 사용)
    void deleteAllByPostId(Long postId);
}
