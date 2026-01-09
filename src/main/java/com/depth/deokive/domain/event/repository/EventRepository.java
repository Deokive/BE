package com.depth.deokive.domain.event.repository;

import com.depth.deokive.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    // 월별 조회용 (Archive ID + Date Range)
    // SportRecord는 1:1이므로 Fetch Join으로 한 방에 가져옴
    @Query("SELECT e FROM Event e " +
            "LEFT JOIN FETCH e.sportRecord " +
            "WHERE e.archive.id = :archiveId " +
            "AND e.date >= :start AND e.date < :end " +
            "ORDER BY e.date ASC")
    List<Event> findAllByArchiveAndDateRange(
            @Param("archiveId") Long archiveId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Modifying
    @Query("DELETE FROM Event e WHERE e.archive.id = :archiveId")
    void deleteByArchiveId(@Param("archiveId") Long archiveId);

    @Query("SELECT COUNT(e) FROM Event e " +
            "WHERE e.archive.id = :archiveId " +
            "AND e.date >= :start AND e.date < :end")
    long countByArchiveIdAndDate(
            @Param("archiveId") Long archiveId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}

