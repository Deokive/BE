package com.depth.deokive.domain.diary.repository;

import com.depth.deokive.domain.diary.entity.DiaryBook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryBookRepository extends JpaRepository<DiaryBook, Long> {
}

