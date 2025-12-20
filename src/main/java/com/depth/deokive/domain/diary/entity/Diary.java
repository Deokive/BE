package com.depth.deokive.domain.diary.entity;

import com.depth.deokive.common.auditor.UserBaseEntity;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.diary.dto.DiaryDto;
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
@Table(name = "diary", indexes = {
    @Index(name = "idx_diary_book_recorded_at", columnList = "diary_book_id, recorded_at DESC")
})
public class Diary extends UserBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

    @Column(nullable = false, length = 7)
    private String color; // 색상 코드 (예: #FF5733)

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_book_id", nullable = false)
    private DiaryBook diaryBook;

    public void update(DiaryDto.Request request) {
        if (request == null) return;

        this.title = nonBlankOrDefault(request.getTitle(), this.title);
        this.content = nonBlankOrDefault(request.getContent(), this.content);
        this.recordedAt = nonBlankOrDefault(request.getRecordedAt(), this.recordedAt);
        this.color = nonBlankOrDefault(request.getColor(), this.color);
        this.visibility = nonBlankOrDefault(request.getVisibility(), this.visibility);
    }

    // TODO: 사실 이게 PATCH 패턴 처리 방식. Validation 에서 체크를 해줘서 빈 값 들어올 일은 없긴 한데... 일단 보류. 리팩토링 단계에서 고려
    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}