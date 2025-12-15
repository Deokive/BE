package com.depth.deokive.domain.event.repository;

import com.depth.deokive.domain.event.entity.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {
}

