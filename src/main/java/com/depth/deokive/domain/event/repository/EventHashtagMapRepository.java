package com.depth.deokive.domain.event.repository;

import com.depth.deokive.domain.event.entity.EventHashtagMap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHashtagMapRepository extends JpaRepository<EventHashtagMap, Long> {
}

