package com.depth.deokive.domain.ticket.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.ticket.dto.TicketDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "ticket", indexes = {
        @Index(name = "idx_ticket_book_created", columnList = "ticket_book_id, created_at DESC, id DESC"),
        @Index(name = "idx_ticket_book_date", columnList = "ticket_book_id, date DESC, id DESC"),
})
public class Ticket extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private LocalDateTime date; // 티켓 유효 일자

    @Column(length = 20)
    private String location; // 티켓 장소

    @Column(length = 20)
    private String seat; // 티켓 좌석 정보

    private String casting;

    private Double score; // 티켓 관련 평점

    @Column(length = 100)
    private String review; // 티켓 관련 리뷰

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_book_id", nullable = false)
    private TicketBook ticketBook;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private File file;

    @Column(name = "original_key")
    private String originalKey;

    public void update(TicketDto.UpdateRequest request, File resolvedFile) {
        if (request == null) return;

        this.title = nonBlankOrDefault(request.getTitle(), this.title);
        this.date = nonBlankOrDefault(request.getDate(), this.date);
        this.location = nonBlankOrDefault(request.getLocation(), this.location);
        this.seat = nonBlankOrDefault(request.getSeat(), this.seat);
        this.casting = nonBlankOrDefault(request.getCasting(), this.casting);
        this.score = nonBlankOrDefault(request.getScore(), this.score);
        this.review = nonBlankOrDefault(request.getReview(), this.review);

        updateFile(resolvedFile);
    }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }

    private void updateFile(File file) {
        this.file = file; // Service Layer에서 철저한 검증 거침
        this.originalKey = (file != null) ? file.getS3ObjectKey() : null;
    }
}