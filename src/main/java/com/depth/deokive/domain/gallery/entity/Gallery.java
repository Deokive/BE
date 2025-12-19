package com.depth.deokive.domain.gallery.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.file.entity.File;
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
@Table(name = "gallery",
    // 이거 주석처리 했다가 풀었다 하면서 성능 비교할 것
    indexes = {
        @Index(name = "idx_gallery_archive_created", columnList = "archive_id, created_at DESC"),
        @Index(name = "idx_gallery_archive_last_modified", columnList = "archive_id, last_modified_at DESC"),
    })
public class Gallery extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_id")
    private Long archiveId; // 조회 성능을 위한 역정규화 컬럼

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gallery_book_id", nullable = false)
    private GalleryBook galleryBook;

    @ManyToOne(fetch = FetchType.LAZY) // 파일 재사용성을 위해 OneToOne 대신 ManyToOne 권장
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

}