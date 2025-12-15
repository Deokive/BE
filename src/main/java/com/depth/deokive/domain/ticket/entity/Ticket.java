package com.depth.deokive.domain.ticket.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
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
@Table(name = "ticket")
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

    @Lob
    private String casting;

    private Integer score; // 티켓 관련 평점

    @Column(length = 100)
    private String review; // 티켓 관련 리뷰

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_book_id", nullable = false)
    private TicketBook ticketBook;
}