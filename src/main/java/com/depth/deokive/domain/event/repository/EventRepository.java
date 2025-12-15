package com.depth.deokive.domain.event.repository;

import com.depth.deokive.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}

