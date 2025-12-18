package com.depth.deokive.domain.diary.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "diary")
public class Diary extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate date; // 사용자 지정 일기 일자

    @Column(nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String color; // 색상 코드 (예: #FF5733)

    @Column(nullable = false, length = 1000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_book_id", nullable = false)
    private DiaryBook diaryBook;
}