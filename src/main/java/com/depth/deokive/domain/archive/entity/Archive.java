package com.depth.deokive.domain.archive.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.post.entity.RepostBook;
import com.depth.deokive.domain.ticket.entity.TicketBook;
import com.depth.deokive.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "archive", indexes = {
        // 1. 마이/친구 아카이브용 (유저별 + 생성/수정일 정렬)
        @Index(name = "idx_archive_user_created", columnList = "user_id, created_at DESC, id DESC"),
        @Index(name = "idx_archive_user_modified", columnList = "user_id, last_modified_at DESC, id DESC"),
})
public class Archive extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Visibility visibility;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Badge badge = Badge.NEWBIE;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_file_id")
    private File bannerFile;

    @OneToOne(mappedBy = "archive", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DiaryBook diaryBook;

    @OneToOne(mappedBy = "archive", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private GalleryBook galleryBook;

    @OneToOne(mappedBy = "archive", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private RepostBook repostBook;

    @OneToOne(mappedBy = "archive", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TicketBook ticketBook;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    public void update(ArchiveDto.UpdateRequest request) {
        if (request == null) return;

        this.title = nonBlankOrDefault(request.getTitle(), this.title);

        // Notice: visibility나 badge 변경 시 Service에서 ArchiveStats도 동기화해야 함
        this.visibility = nonBlankOrDefault(request.getVisibility(), this.visibility);
    }

    public void updateBanner(File file) {
        this.bannerFile = file;

        if (file != null) {
            this.thumbnailKey = ThumbnailUtils.getMediumThumbnailKey(file.getS3ObjectKey());
        } else {
            this.thumbnailKey = null;
        }
    }

    // CascadeType.ALL 로 인해 연관된 북들도 함께 저장/삭제됨
    public void setBooks(DiaryBook diary, TicketBook ticket, GalleryBook gallery, RepostBook repost) {
        this.diaryBook = diary;
        this.ticketBook = ticket;
        this.galleryBook = gallery;
        this.repostBook = repost;
    }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) { return newValue != null ? newValue : currentValue; }
}
