package com.depth.deokive.domain.event.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.archive.entity.Archive;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name= "event")
public class Event extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(nullable = false)
    private String title;

    @Builder.Default
    @Column(nullable = false)
    private boolean hasTime = false; // 사용자 시간 설정 On-Off 여부

    @Column(nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String color; // 색상 코드 (예: #FF5733)

    @Builder.Default
    @Column(nullable = false)
    private boolean isSportType = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archive_id", nullable = false)
    private Archive archive;
}