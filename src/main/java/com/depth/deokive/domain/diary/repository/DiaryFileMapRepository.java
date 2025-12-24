package com.depth.deokive.domain.diary.repository;

import com.depth.deokive.domain.diary.entity.DiaryFileMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DiaryFileMapRepository extends JpaRepository<DiaryFileMap, Long> {
    List<DiaryFileMap> findAllByDiaryIdOrderBySequenceAsc(Long diaryId);

    @Modifying
    @Query("DELETE FROM DiaryFileMap dfm WHERE dfm.diary.id = :diaryId")
    void deleteAllByDiaryId(Long diaryId);

    @Modifying
    @Query("DELETE FROM DiaryFileMap dfm WHERE dfm.diary.id IN (SELECT d.id FROM Diary d WHERE d.diaryBook.id = :bookId)")
    void deleteFileMapsByBookId(@Param("bookId") Long bookId);
}

