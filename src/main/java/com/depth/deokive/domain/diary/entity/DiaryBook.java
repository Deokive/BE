package com.depth.deokive.domain.diary.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.archive.entity.Archive;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "diary_book")
public class DiaryBook extends TimeBaseEntity {
    @Id
    private Long id;

    @Column(nullable = false)
    private String title;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archive_id")
    private Archive archive;
}