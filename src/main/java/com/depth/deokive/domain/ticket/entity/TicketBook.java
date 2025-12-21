package com.depth.deokive.domain.ticket.entity;

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
@Table(name= "ticket_book")
public class TicketBook extends TimeBaseEntity {
    @Id
    private Long id; // Archive ID와 동일

    @Column(nullable = false)
    private String title;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archive_id")
    private Archive archive;

    public void updateTitle(String title) {
        this.title = title;
    }
}
