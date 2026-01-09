package com.depth.deokive.domain.sticker.entity;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.sticker.entity.enums.StickerType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(
        name = "sticker",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sticker_archive_date", // 한 아카이브 내에서 같은 날짜에 중복 스티커 불가
                        columnNames = {"archive_id", "date"}
                )
        },
        indexes = {
                @Index(name = "idx_sticker_archive_date", columnList = "archive_id, date")
        }
)
public class Sticker {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    StickerType stickerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archive_id", nullable = false)
    private Archive archive;

    public void update(StickerType stickerType, LocalDate date) {
        this.stickerType = nonBlankOrDefault(stickerType, this.stickerType);
        this.date = nonBlankOrDefault(date, this.date);
    }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}
