package com.depth.deokive.domain.ticket.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
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
@Table(
    name= "ticket_file_map",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_file_map", columnNames = {"ticket_id", "file_id"})
    },
    indexes = {
        @Index(name = "idx_ticket_file_map_role_seq", columnList = "ticket_id, media_role, sequence")
    }
)
public class TicketFileMap extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaRole mediaRole;

    @Column(nullable = false)
    private Integer sequence;
}
