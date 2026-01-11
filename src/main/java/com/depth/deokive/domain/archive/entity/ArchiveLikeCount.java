package com.depth.deokive.domain.archive.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "archive_like_count")
public class ArchiveLikeCount {

    @Id
    @Column(name = "archive_id")
    private Long archiveId; // Archive ID와 동일 (Shared PK)

    @Column(nullable = false)
    private Long count;

    public static ArchiveLikeCount create(Long archiveId) {
        return ArchiveLikeCount.builder()
                .archiveId(archiveId)
                .count(0L)
                .build();
    }
}