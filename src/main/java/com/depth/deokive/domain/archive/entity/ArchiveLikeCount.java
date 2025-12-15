package com.depth.deokive.domain.archive.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "archive_like_count")
public class ArchiveLikeCount {

    @Id
    @Column(name = "archive_id")
    private Long archiveId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archive_id")
    private Archive archive;

    @Builder.Default
    @Column(nullable = false)
    private long likeCount=0;
}