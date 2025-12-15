package com.depth.deokive.domain.archive.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "archive_view_count")
public class ArchiveViewCount {
    @Id
    @Column(name = "archive_id")
    private Long archiveId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archive_id")
    private Archive archive;

    @Builder.Default
    @Column(nullable = false)
    private long viewCount = 0;
}
