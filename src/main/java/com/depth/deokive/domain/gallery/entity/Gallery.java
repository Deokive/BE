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
@Table(name = "gallery")
public class Gallery extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gallery_book_id", nullable = false)
    private GalleryBook galleryBook;

    @ManyToOne(fetch = FetchType.LAZY) // 파일 재사용성을 위해 OneToOne 대신 ManyToOne 권장
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    // @Column(nullable = false)
    // private Integer sequence; // 앨범 내 정렬 순서 (사용자 지정) // TODO: 기획측에서 아직 의도는 안했지만, 필요할 가능성이 높음 (추후 확장)
}