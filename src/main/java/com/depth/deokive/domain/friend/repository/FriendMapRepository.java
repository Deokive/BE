package com.depth.deokive.domain.friend.repository;

import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendMapRepository extends JpaRepository<FriendMap, Long> {

    boolean existsByUserIdAndFriendIdAndFriendStatus(Long userId, Long friendId, FriendStatus status);
}