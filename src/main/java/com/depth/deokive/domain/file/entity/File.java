package com.depth.deokive.domain.file.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.common.auditor.UserBaseEntity;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "files")
public class File extends UserBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String s3ObjectKey;

    @Column(nullable = false, length = 1024)
    private String filename; // 원본 파일명

    @Column(nullable = false, length = 1024) // TODO: FILE_PATH DELETE
    private String filePath; // CDN URL (bucketName 노출 방지)

    @Column(nullable = false)
    private Long fileSize; // 파일 크기 (바이트 단위)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_file_id")
    private File originalFile; // 썸네일인 경우 원본 파일 참조

    @Column(nullable = false)
    @Builder.Default
    private Boolean isThumbnail = false; // 썸네일 여부
}
