package com.depth.deokive.domain.archive.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
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

import java.util.ArrayList;
import java.util.List;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "archive")
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

    @OneToOne(mappedBy = "archive", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ArchiveViewCount viewCount;

    @OneToOne(mappedBy = "archive", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ArchiveLikeCount likeCount;

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

    public void update(ArchiveDto.UpdateRequest request) {
        if (request == null) return;

        this.title = nonBlankOrDefault(request.getTitle(), this.title);
        this.visibility = nonBlankOrDefault(request.getVisibility(), this.visibility);
    }

    public void updateBanner(File file) {
        this.bannerFile = file;
    }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) { return newValue != null ? newValue : currentValue; }
}