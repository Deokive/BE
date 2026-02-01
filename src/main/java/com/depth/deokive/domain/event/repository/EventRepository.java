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
    // 여러 달에 걸친 이벤트도 조회하기 위해 날짜 범위 겹침 검사
    // 조건: startDate <= 월의_마지막일 AND endDate >= 월의_첫날
    @Query("SELECT e FROM Event e " +
            "LEFT JOIN FETCH e.sportRecord " +
            "WHERE e.archive.id = :archiveId " +
            "AND e.startDate <= :end AND e.endDate >= :start " +
            "ORDER BY e.startDate ASC")
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
            "AND e.startDate <= :end AND e.endDate >= :start")
    long countByArchiveIdAndDate(
            @Param("archiveId") Long archiveId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}

