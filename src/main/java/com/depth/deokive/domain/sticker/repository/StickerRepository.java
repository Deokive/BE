package com.depth.deokive.domain.sticker.repository;

import com.depth.deokive.domain.sticker.entity.Sticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StickerRepository extends JpaRepository<Sticker, Long> {
    @Modifying
    @Query("DELETE FROM Sticker s WHERE s.archive.id = :archiveId")
    void deleteByArchiveId(@Param("archiveId") Long archiveId);

    // 🧐 Event의 경우와 다른데요? 그때는 시간을 다룸 (LocalDateTime) -> 정확성 이슈가 있었음
    List<Sticker> findAllByArchiveIdAndDateBetweenOrderByDateAsc(Long archiveId, LocalDate startDate, LocalDate endDate);

    boolean existsByArchiveIdAndDate(Long archiveId, LocalDate date);
}
