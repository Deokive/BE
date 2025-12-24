package com.depth.deokive.domain.event.repository;

import com.depth.deokive.domain.event.entity.EventHashtagMap;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventHashtagMapRepository extends JpaRepository<EventHashtagMap, Long> {
    @Query("SELECT ehm.hashtag.name FROM EventHashtagMap ehm WHERE ehm.event.id = :eventId")
    List<String> findHashtagNamesByEventId(@Param("eventId") Long eventId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM EventHashtagMap ehm WHERE ehm.event.id = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId); // 보통 Hashtags 는 많을 수 있음 -> 한번에 날림

    @EntityGraph(attributePaths = {"hashtag"})
    List<EventHashtagMap> findAllByEventId(Long eventId);

    @EntityGraph(attributePaths = {"hashtag"})
    List<EventHashtagMap> findAllByEventIdIn(List<Long> eventIds);

    @Modifying
    @Query("DELETE FROM EventHashtagMap ehm WHERE ehm.event.id IN (SELECT e.id FROM Event e WHERE e.archive.id = :archiveId)")
    void deleteByArchiveId(@Param("archiveId") Long archiveId);
}

