package com.depth.deokive.domain.archive.service;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.repository.ArchiveQueryRepository;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final ArchiveQueryRepository archiveQueryRepository;
    private final FriendMapRepository friendMapRepository;

    // --- Core Repositories ---
    private final ArchiveRepository archiveRepository;
    private final ArchiveViewCountRepository viewCountRepository;
    private final ArchiveLikeCountRepository likeCountRepository;
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


    // 내 아카이브 목록 조회
    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getMyArchives(Long userId, Pageable pageable) {
        // 1. 조회
        Page<ArchiveDto.Response> page = archiveQueryRepository.searchArchives(userId, true, pageable);

        // 2. 인덱스 범위 검증
        validateIndexBounds(pageable, page);

        return ArchiveDto.PageListResponse.of(page);
    }

    // 친구 아카이브 목록 조회
    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getFriendArchives(Long myUserId, Long friendId, Pageable pageable) {
        // 1. 친구 관계 검증
        validateFriendRelationship(myUserId, friendId);

        // 2. 조회
        Page<ArchiveDto.Response> page = archiveQueryRepository.searchArchives(friendId, false, pageable);

        // 3. 인덱스 범위 검증
        validateIndexBounds(pageable, page);

        return ArchiveDto.PageListResponse.of(page);
    }

    // 핫피드 목록 조회
    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getHotArchives(Pageable pageable) {

        // 1. 핫피드 조회
        Page<ArchiveDto.Response> page = archiveQueryRepository.searchHotArchives(pageable);

        // 2. 페이지 범위 검증(데이터 없는데 페이지 요청 시 -> 404에러)
        validateIndexBounds(pageable, page);

        return ArchiveDto.PageListResponse.of(page);
    }

    // 친구 관계 검증 로직 분리 (Clean Code)
    private void validateFriendRelationship(Long myUserId, Long friendId) {
        // 친구 존재 여부 확인
        if (!userRepository.existsById(friendId)) {
            throw new RestException(ErrorCode.USER_NOT_FOUND); // 존재하지 X -> 404에러
        }

        // 친구 관계 확인(ACCEPTED인지)
        boolean isFriend = friendMapRepository.existsFriendship(myUserId, friendId, FriendStatus.ACCEPTED);

        if (!isFriend) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 친구 X -> 403 에러
        }
    }

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

        archiveRepository.save(archive);

        // SEQ 3. Sub Domain Books 자동 생성
        createSubDomainBooks(archive);

        // SEQ 4. Counts 초기화 (1:1 식별 관계) -> // TODO: DTO를 굳이 둬야할지 고민중
        viewCountRepository.save(ArchiveViewCount.builder().archive(archive).viewCount(0).build());
        likeCountRepository.save(ArchiveLikeCount.builder().archive(archive).likeCount(0).build());

        // SEQ 5. 배너 이미지 연결
        String bannerUrl = null;
        if (request.getBannerImageId() != null) {
            File banner = fileRepository.findById(request.getBannerImageId())
                    .orElseThrow(() -> new RestException(ErrorCode.FILE_NOT_FOUND));
            archive.updateBanner(banner);
            bannerUrl = banner.getFilePath();
        }

        // p: archive, bannerUrl, viewCount, likeCount, isLiked, isOwner
        return ArchiveDto.Response.of(archive, bannerUrl, 0, 0, false, true);
    }

    @Transactional
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
        // TODO: 동시성 이슈 고려 시 Redis 사용. 현재는 단순 증가. 추후 개선
        ArchiveViewCount viewCountEntity = viewCountRepository.findById(archiveId)
                .orElseGet(() -> viewCountRepository.save(ArchiveViewCount.builder().archive(archive).build()));
        viewCountEntity.increment();

        // SEQ 5. 데이터 조회 : 좋아요 수, 조회수, isLiked, isOwner, bannerUrl, archive
        ArchiveLikeCount likeCountEntity = likeCountRepository.findById(archiveId).orElse(null);
        String bannerUrl = (archive.getBannerFile() != null) ? archive.getBannerFile().getFilePath() : null;
        boolean isLiked = (viewerId != null) && likeRepository.existsByArchiveIdAndUserId(archiveId, viewerId);

        return ArchiveDto.Response.of(
                archive,
                bannerUrl,
                viewCountEntity.getViewCount(), // 조회수: 조회 시점 값 반환
                (likeCountEntity != null) ? likeCountEntity.getLikeCount() : 0, // 좋아요 수
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
        String bannerUrl = (archive.getBannerFile() != null) ? archive.getBannerFile().getFilePath() : null;
        if (request.getBannerImageId() != null) {
            if (request.getBannerImageId() == -1L) {
                archive.updateBanner(null);
                bannerUrl = null;
            } else {
                File newBanner = fileRepository.findById(request.getBannerImageId())
                        .orElseThrow(() -> new RestException(ErrorCode.FILE_NOT_FOUND));
                archive.updateBanner(newBanner);
                bannerUrl = newBanner.getFilePath();
            }
        }

        // SEQ 5. 리턴용 조회
        long viewCount = viewCountRepository.findById(archiveId).map(ArchiveViewCount::getViewCount).orElse(0L);
        long likeCount = likeCountRepository.findById(archiveId).map(ArchiveLikeCount::getLikeCount).orElse(0L);
        boolean isLiked = likeRepository.existsByArchiveIdAndUserId(archiveId, user.getUserId());

        return ArchiveDto.Response.of(archive, bannerUrl, viewCount, likeCount, isLiked, true);
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

        // Step 2. Root 삭제
        // Cascade -> Sub Domain 삭제: DiaryBook, GalleryBook, TicketBook, RepostBook, ViewCount, LikeCount, Banner
        archiveRepository.delete(archive);

        log.info("🟢 Archive Delete Completed.");
    }

    // -------- Helper Methods
    private void createSubDomainBooks(Archive archive) {
        String baseTitle = archive.getTitle();

        // 이거 때문에 정적 팩터리 메서드 만드는게 좀 귀찮 -> 리펙터링 단계에서 고려하고 바꿔야겠으면 수정하는걸로
        diaryBookRepository.save(DiaryBook.builder().archive(archive).title(baseTitle + "의 다이어리").build());
        galleryBookRepository.save(GalleryBook.builder().archive(archive).title(baseTitle + "의 갤러리").build());
        ticketBookRepository.save(TicketBook.builder().archive(archive).title(baseTitle + "의 티켓북").build());
        repostBookRepository.save(RepostBook.builder().archive(archive).title(baseTitle + "의 스크랩북").build());
    }

    private void validateOwner(Archive archive, UserPrincipal user) {
        if (!archive.getUser().getId().equals(user.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void checkVisibility(Long viewerId, boolean isOwner, Archive archive) {
        if (isOwner) return; // 주인은 모든 상태 볼 수 있음

        if (archive.getVisibility() == Visibility.PRIVATE) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }

        if (archive.getVisibility() == Visibility.RESTRICTED) {
            // TODO: 친구 관계 확인 로직 구현 필요
            // 현재는 친구 기능이 없으므로 RESTRICTED도 접근 불가 처리
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // 공통 검증 로직(원래 있으면 재사용, 없으면 추가)
    public void validateIndexBounds(Pageable pageable, Page<?> pageData) {
        // 요청한 페이지 -> 전체 페이지 수, 데이터가 아예 없는게 아니면 404 예외처리
        if (pageable.getPageNumber() > 0 && pageData.getTotalPages() <= pageable.getPageNumber()) {
            throw new RestException(ErrorCode.DB_DATA_NOT_FOUND);
        }
    }
}