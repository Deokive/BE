package com.depth.deokive.domain.archive.service;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.depth.deokive.domain.archive.entity.*;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.archive.repository.*;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.event.repository.EventHashtagMapRepository;
import com.depth.deokive.domain.event.repository.EventRepository;
import com.depth.deokive.domain.event.repository.SportRecordRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.gallery.repository.GalleryBookRepository;
import com.depth.deokive.domain.gallery.repository.GalleryRepository;
import com.depth.deokive.domain.post.entity.RepostBook;
import com.depth.deokive.domain.post.repository.RepostBookRepository;
import com.depth.deokive.domain.post.repository.RepostRepository;
import com.depth.deokive.domain.post.repository.RepostTabRepository;
import com.depth.deokive.domain.sticker.repository.StickerRepository;
import com.depth.deokive.domain.ticket.entity.TicketBook;
import com.depth.deokive.domain.ticket.repository.TicketBookRepository;
import com.depth.deokive.domain.ticket.repository.TicketRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final FileService fileService;

    private final ArchiveQueryRepository archiveQueryRepository;
    private final FriendMapRepository friendMapRepository;

    // --- Core Repositories ---
    private final ArchiveRepository archiveRepository;
    private final ArchiveLikeRepository likeRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    // --- Sub-Domain Book Repositories ---
    private final DiaryBookRepository diaryBookRepository;
    private final GalleryBookRepository galleryBookRepository;
    private final TicketBookRepository ticketBookRepository;
    private final RepostBookRepository repostBookRepository;

    // --- Sub-Domain Content Repositories (For Bulk Delete) ---
    private final EventRepository eventRepository;
    private final EventHashtagMapRepository eventHashtagMapRepository;
    private final SportRecordRepository sportRecordRepository;

    private final DiaryRepository diaryRepository;
    private final DiaryFileMapRepository diaryFileMapRepository;

    private final TicketRepository ticketRepository;
    private final GalleryRepository galleryRepository;

    private final RepostRepository repostRepository;
    private final RepostTabRepository repostTabRepository;
    private final StickerRepository stickerRepository;

    @Transactional
    public ArchiveDto.Response createArchive(UserPrincipal userPrincipal, ArchiveDto.CreateRequest request) {
        // SEQ 1. User 조회
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        // SEQ 2. Archive 생성
        Archive archive = Archive.builder()
                .user(foundUser)
                .title(request.getTitle())
                .visibility(request.getVisibility())
                .badge(Badge.NEWBIE) // 생성 시점에선 기본 뱃지로 들어감
                .build();

        // SEQ 3. Banner 파일 연결 (기존에서 순서를 변경 -> 추후 IDOR 취약점 방지 처리 로직 넣을거임)
        if (request.getBannerImageId() != null) {
            File bannerFile = fileService.validateFileOwner(request.getBannerImageId(), foundUser.getId());
            archive.updateBanner(bannerFile);
        }

        // SEQ 4. Sub Domain Books 생성 및 연결 (Cascade 준비)
        linkSubDomainBooks(archive);

        // SEQ 5. 저장 (Archive + Books Cascade)
        archiveRepository.save(archive);


        // SEQ 6. Response
        String bannerUrl = (archive.getBannerFile() != null)
                ? archive.getBannerFile().getFilePath()
                : null;

        // p: archive, bannerUrl, viewCount, likeCount, isLiked, isOwner
        return ArchiveDto.Response.of(archive, bannerUrl, 0, 0, false, true);
    }

    @Transactional // viewCount 바꿔서 readOnly가 아닌거임
    public ArchiveDto.Response getArchiveDetail(UserPrincipal userPrincipal, Long archiveId) {
        // SEQ 1. Fetch Join을 사용하여 Archive + User 조회 (N+1 방지)
        Archive archive = archiveRepository.findByIdWithUser(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. Viewer & Owner 판별
        Long viewerId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
        boolean isOwner = archive.getUser().getId().equals(viewerId);

        // SEQ 3. 권한 체크 -> 친구면 RESTRICTED 까지, 비회원이면 PUBLIC까지
        checkVisibility(viewerId, isOwner, archive);

        // SEQ 4. 조회수 증가 (Dirty Checking)
        archive.increaseViewCount();

        // SEQ 5. 데이터 조회 : 좋아요 수, 조회수, isLiked, isOwner, bannerUrl, archive
        // ArchiveLikeCount likeCountEntity = likeCountRepository.findById(archiveId).orElse(null);
        String bannerUrl = (archive.getBannerFile() != null) ? archive.getBannerFile().getFilePath() : null;
        boolean isLiked = (viewerId != null) && likeRepository.existsByArchiveIdAndUserId(archiveId, viewerId);

        return ArchiveDto.Response.of(
                archive,
                bannerUrl,
                archive.getViewCount(),
                archive.getLikeCount(),
                isLiked,
                isOwner
        );
    }

    @Transactional
    public ArchiveDto.Response updateArchive(UserPrincipal user, Long archiveId, ArchiveDto.UpdateRequest request) {
        // SEQ 1. Archive 조회
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유자 검증
        validateOwner(archive, user);

        // SEQ 3. 기본 정보 수정
        archive.update(request); // 여기서 bannerUrl 은 처리하지 않음

        // SEQ 4. 배너 수정
        String bannerUrl = updateBannerImage(archive, request.getBannerImageId(), user.getUserId());

        // SEQ 5. 리턴용 조회
        boolean isLiked = likeRepository.existsByArchiveIdAndUserId(archiveId, user.getUserId());

        return ArchiveDto.Response.of(
                archive,
                bannerUrl,
                archive.getViewCount(),
                archive.getLikeCount(),
                isLiked,
                true
        );
    }

    @Transactional
    public void deleteArchive(UserPrincipal user, Long archiveId) {
        // SEQ 1. Archive 조회
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유자 검증
        validateOwner(archive, user);

        // SEQ 3. 명시적 Bulk & Cascade 삭제
        // JPA Cascade는 N+1 문제가 발생하므로, JPQL Bulk Delete로 성능 최적화
        // FK 제약조건을 고려하여 자식 -> 부모 순서로 삭제
        log.info("➡️ Archive Delete Start deleting contents of archiveId: {}", archiveId);

        // Step 1. Sub Domain Contents 삭제
        // 1️⃣ Event Domain Cleanup
        eventHashtagMapRepository.deleteByArchiveId(archiveId); // Level 3
        sportRecordRepository.deleteByArchiveId(archiveId);     // Level 3
        eventRepository.deleteByArchiveId(archiveId);           // Level 2

        // 2️⃣ Diary Domain Cleanup (BookId == ArchiveId)
        diaryFileMapRepository.deleteFileMapsByBookId(archiveId); // Level 3
        diaryRepository.deleteByBookId(archiveId);                // Level 2

        // 3️⃣. Ticket Domain Cleanup
        ticketRepository.deleteByBookId(archiveId);

        // 4️⃣. Gallery Domain Cleanup
        galleryRepository.deleteByArchiveId(archiveId); // Gallery는 archiveId 컬럼이 역정규화되어 있어 Book 조인 없이 바로 삭제 가능

        // 5️⃣ Repost Domain Cleanup
        repostRepository.deleteByBookId(archiveId);     // Level 3 (Repost)
        repostTabRepository.deleteByBookId(archiveId);  // Level 2 (Tab)

        // 6️⃣ Sticker Domain Cleanup
        stickerRepository.deleteByArchiveId(archiveId); // Level 2

        // Step 2. 명시적 삭제 - Like
        likeRepository.deleteByArchiveId(archiveId);

        // Step 3. Root 삭제
        // Cascade -> Sub Domain 삭제: DiaryBook, GalleryBook, TicketBook, RepostBook, Banner
        archiveRepository.delete(archive);

        log.info("🟢 Archive Delete Completed.");
    }

    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getGlobalFeed(ArchiveDto.FeedRequest request) {
        // 무조건 PUBLIC & 전체 유저 대상
        Page<ArchiveDto.FeedResponse> page = archiveQueryRepository.searchArchiveFeed(
                null, // filterUserId
                List.of(Visibility.PUBLIC),
                request.toPageable()
        );

        String title = "hotScore".equals(request.getSort()) ? "지금 핫한 피드" : "최신 아카이브 피드";

        return ArchiveDto.PageListResponse.of(title, page);
    }

    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getUserArchives(
            UserPrincipal userPrincipal,
            Long targetUserId,
            ArchiveDto.FeedRequest request
    ) {
        List<Visibility> visibilities;
        String pageTitle;

        // 본인 확인
        if (userPrincipal != null && userPrincipal.getUserId().equals(targetUserId)) {
            visibilities = List.of(Visibility.PUBLIC, Visibility.RESTRICTED, Visibility.PRIVATE);
            pageTitle = "마이 아카이브";
        } else {
            // 친구 확인 (Stub: 추후 친구 로직 구현 시 대체)
            Long viewerId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
            boolean isFriend = isFriendCheck(viewerId, targetUserId);
            visibilities = isFriend
                    ? List.of(Visibility.PUBLIC, Visibility.RESTRICTED)
                    : List.of(Visibility.PUBLIC);

            // 닉네임 조회 (Optional, UX용)
            String nickname = userRepository.findById(targetUserId)
                    .map(User::getNickname)
                    .orElse("알 수 없는 사용자");
            pageTitle = nickname + "님의 아카이브";
        }

        Page<ArchiveDto.FeedResponse> page = archiveQueryRepository.searchArchiveFeed(
                targetUserId,
                visibilities,
                request.toPageable()
        );

        return ArchiveDto.PageListResponse.of(pageTitle, page);
    }

    // -------- Helper Methods
    private void linkSubDomainBooks(Archive archive) {
        String baseTitle = archive.getTitle();

        // Book 객체 생성 (아직 저장 X)
        DiaryBook diary = DiaryBook.builder().archive(archive).title(baseTitle + "의 다이어리").build();
        GalleryBook gallery = GalleryBook.builder().archive(archive).title(baseTitle + "의 갤러리").build();
        TicketBook ticket = TicketBook.builder().archive(archive).title(baseTitle + "의 티켓북").build();
        RepostBook repost = RepostBook.builder().archive(archive).title(baseTitle + "의 스크랩북").build();

        // Archive에 연결 (Cascade 동작 트리거)
        archive.setBooks(diary, ticket, gallery, repost);
    }

    private void validateOwner(Archive archive, UserPrincipal user) {
        if (!archive.getUser().getId().equals(user.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // TODO: 다른 도메인과 메서드 규칙 일관성 맞춰둘 것
    private void checkVisibility(Long viewerId, boolean isOwner, Archive archive) {
        if (isOwner) return; // 주인은 모든 상태 볼 수 있음

        if (archive.getVisibility() == Visibility.PRIVATE) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }

        if (archive.getVisibility() == Visibility.RESTRICTED) {
            if (!isFriendCheck(viewerId, archive.getUser().getId())) {
                throw new RestException(ErrorCode.AUTH_FORBIDDEN);
            }
        }
    }

    private boolean isFriendCheck(Long viewerId, Long ownerId) {
        if (viewerId == null) return false;

        // JPQL 쿼리를 통해 친구 여부 확인
        return friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(
                viewerId,
                ownerId,
                FriendStatus.ACCEPTED
        );
    }

    private String updateBannerImage(Archive archive, Long newFileId, Long userId) {
        if (newFileId == null) return null;

        if (newFileId == -1L) {
            // 삭제 요청: 기존 파일 연결 해제
            archive.updateBanner(null); // 실제 S3 삭제는 배치 처리로 위임 (Lazy Deletion)
            return null;
        } else {
            // 변경 요청

            File newBanner = fileService.validateFileOwner(newFileId, userId);
            archive.updateBanner(newBanner);
            return newBanner.getFilePath();
        }
    }
}