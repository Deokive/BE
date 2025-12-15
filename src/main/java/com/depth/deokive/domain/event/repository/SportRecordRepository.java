package com.depth.deokive.domain.event.repository;

import com.depth.deokive.domain.event.entity.SportRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SportRecordRepository extends JpaRepository<SportRecord, Long> {
}

