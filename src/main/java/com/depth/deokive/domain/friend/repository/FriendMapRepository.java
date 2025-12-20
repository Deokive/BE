package com.depth.deokive.domain.friend.repository;

import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendMapRepository extends JpaRepository<FriendMap, Long> {

    // 친구 관계 존재 여부 확인 (JPQL로 명확하게 정의)
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END " +
            "FROM FriendMap f " +
            "WHERE f.user.id = :userId AND f.friend.id = :friendId AND f.friendStatus = :status")
    boolean existsFriendship(@Param("userId") Long userId,
                             @Param("friendId") Long friendId,
                             @Param("status") FriendStatus status);
}