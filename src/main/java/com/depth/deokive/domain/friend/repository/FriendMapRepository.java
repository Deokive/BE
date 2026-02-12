package com.depth.deokive.domain.friend.repository;

import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FriendMapRepository extends JpaRepository<FriendMap, Long> {

    // 특정 유저와 친구 관계 기록 조회
    Optional<FriendMap> findByUserIdAndFriendId(Long userId, Long friendId);

    // 상태 여부 확인
    boolean existsByUserIdAndFriendIdAndFriendStatus(Long userId, Long friendId, FriendStatus status);

    // 만료된 CANCELED/REJECTED 레코드 일괄 삭제
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FriendMap f WHERE f.friendStatus IN :statuses AND f.lastModifiedAt < :cutoff")
    int deleteExpiredByStatuses(@Param("statuses") List<FriendStatus> statuses,
                                @Param("cutoff") LocalDateTime cutoff);
}