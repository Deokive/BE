package com.depth.deokive.domain.event.repository;

import com.depth.deokive.domain.event.entity.SportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SportRecordRepository extends JpaRepository<SportRecord, Long> {
    @Modifying // SportRecord는 Event와 1:1이며 EventId를 PK로 씀. Event를 통해 Archive를 찾아 삭제
    @Query("DELETE FROM SportRecord sr WHERE sr.event.id IN (SELECT e.id FROM Event e WHERE e.archive.id = :archiveId)")
    void deleteByArchiveId(@Param("archiveId") Long archiveId);
}

