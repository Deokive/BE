package com.depth.deokive.domain.friend.repository;

import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FriendMapRepository extends JpaRepository<FriendMap, Long> {

    // 특정 유저와 친구 관계 기록 조회
    Optional<FriendMap> findByUserIdAndFriendId(Long userId, Long friendId);

    // 상태 여부 확인
    boolean existsByUserIdAndFriendIdAndFriendStatus(Long userId, Long friendId, FriendStatus status);
}