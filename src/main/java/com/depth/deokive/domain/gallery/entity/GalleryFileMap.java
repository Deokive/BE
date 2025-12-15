package com.depth.deokive.domain.gallery.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
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
    name= "gallery_file_map",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_gallery_file_map", columnNames = {"gallery_id", "file_id"})
    },
    indexes = {
        @Index(name = "idx_gallery_file_map_role_seq", columnList = "gallery_id, media_role, sequence")
    }
)
public class GalleryFileMap extends TimeBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gallery_id", nullable = false)
    private Gallery gallery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaRole mediaRole;

    @Column(nullable = false)
    private Integer sequence;
}