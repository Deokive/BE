package com.depth.deokive.domain.diary.repository;

import com.depth.deokive.domain.diary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiaryRepository extends JpaRepository<Diary, Long> {
    @Modifying
    @Query("DELETE FROM Diary d WHERE d.diaryBook.id = :bookId")
    void deleteByBookId(@Param("bookId") Long bookId);
}

