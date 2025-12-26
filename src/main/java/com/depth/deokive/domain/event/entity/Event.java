package com.depth.deokive.domain.event.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.event.dto.EventDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.ticket.dto.TicketDto;
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

    @OneToOne(mappedBy = "event", fetch = FetchType.LAZY)
    private SportRecord sportRecord;

    public void update(EventDto.Request request, LocalDateTime recordAt) {
        if (request == null) return;

        this.title = nonBlankOrDefault(request.getTitle(), this.title);
        this.date = nonBlankOrDefault(recordAt, this.date);

        this.hasTime = nonBlankOrDefault(request.getHasTime(), this.hasTime);
        this.color = nonBlankOrDefault(request.getColor(), this.color);
        this.isSportType = nonBlankOrDefault(request.getIsSportType(), this.isSportType);
    }

    public void deleteSportRecord() { this.sportRecord = null; }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}