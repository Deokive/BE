package com.depth.deokive.domain.sticker.repository;

import com.depth.deokive.domain.sticker.entity.Sticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StickerRepository extends JpaRepository<Sticker, Long> {
    @Modifying
    @Query("DELETE FROM Sticker s WHERE s.archive.id = :archiveId")
    void deleteByArchiveId(@Param("archiveId") Long archiveId);
}
