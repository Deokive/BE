package com.depth.deokive.domain.archive.service;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.enums.ViewLikeDomain;
import com.depth.deokive.common.service.ArchiveGuard;
import com.depth.deokive.common.service.LikeRedisService;
import com.depth.deokive.common.service.PaginationCountCacheService;
import com.depth.deokive.common.service.PaginationIdCacheService;
import com.depth.deokive.common.service.RedisViewService;
import com.depth.deokive.common.util.ClientUtils;
import com.depth.deokive.common.util.FileUrlUtils;
import com.depth.deokive.common.util.PageUtils;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.depth.deokive.domain.archive.entity.*;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.domain.archive.repository.*;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.event.repository.EventHashtagMapRepository;
import com.depth.deokive.domain.event.repository.EventRepository;
import com.depth.deokive.domain.event.repository.SportRecordRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.gallery.repository.GalleryRepository;
import com.depth.deokive.domain.post.entity.RepostBook;
import com.depth.deokive.domain.post.repository.RepostRepository;
import com.depth.deokive.domain.post.repository.RepostTabRepository;
import com.depth.deokive.domain.sticker.repository.StickerRepository;
import com.depth.deokive.domain.ticket.entity.TicketBook;
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
    private final ArchiveGuard archiveGuard;
    private final RedisViewService redisViewService;

    // --- Core Repositories ---
    private final ArchiveRepository archiveRepository;
    private final ArchiveLikeRepository likeRepository;
    private final UserRepository userRepository;
    private final ArchiveStatsRepository archiveStatsRepository;
    private final ArchiveQueryRepository archiveQueryRepository;
    private final LikeRedisService likeRedisService;
    private final PaginationCountCacheService paginationCountCacheService;
    private final PaginationIdCacheService paginationIdCacheService;

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
            archive.updateBanner(bannerFile); // 여기서 자동으로 썸네일도 업데이트 될거임 ㅇㅇ
        }

        // SEQ 4. Sub Domain Books 생성 및 연결 (Cascade 준비)
        linkSubDomainBooks(archive);

        // SEQ 5. 저장 (Archive + Books Cascade)
        archiveRepository.save(archive);

        // SEQ 6. ArchiveStats 생성 및 저장 (Sync OK)
        ArchiveStats stats = ArchiveStats.create(archive);
        archiveStatsRepository.save(stats);

        // SEQ 7. 페이지네이션 캐시 무효화
        paginationCountCacheService.evictByPrefix("archive");
        paginationIdCacheService.evictByPrefix("archive");

        // SEQ 8. Response
        String bannerUrl = (archive.getBannerFile() != null)
                ? FileUrlUtils.buildCdnUrl(archive.getBannerFile().getS3ObjectKey())
                : null;

        // p: archive, bannerUrl, viewCount, likeCount, isLiked, isOwner
        return ArchiveDto.Response.of(archive, bannerUrl, 0, 0, false, true);
    }

    @Transactional // viewCount 바꿔서 readOnly가 아닌거임
    public ArchiveDto.Response getArchiveDetail(
            UserPrincipal userPrincipal,
            Long archiveId,
            HttpServletRequest request
    ) {
        // SEQ 1. Fetch Join을 사용하여 Archive + User 조회 (N+1 방지)
        Archive archive = archiveRepository.findByIdWithUser(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. Viewer & Owner 판별
        Long viewerId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
        boolean isOwner = archive.getUser().getId().equals(viewerId);

        // SEQ 3. 권한 체크 -> 친구면 RESTRICTED 까지, 비회원이면 PUBLIC까지
        archiveGuard.checkArchiveReadPermission(archive, userPrincipal);

        // SEQ 4. 통계 정보 -> Stats 테이블에서 조회 (없으면 뭔가 문제있는거니까 생성->방ㅇ로직)
        ArchiveStats stats = archiveStatsRepository.findById(archiveId)
                .orElseGet(() -> {
                    ArchiveStats newStats = ArchiveStats.create(archive);
                    archiveStatsRepository.save(newStats);
                    return newStats;
                });

        // SEQ 5. 실시간 좋아요 수 조회
        Long realTimeLikeCount = likeRedisService.getCount(
                ViewLikeDomain.ARCHIVE,
                archiveId,
                () -> likeRepository.findAllUserIdsByArchiveId(archiveId), // Warming용 DB Loader
                () -> {}
        );

        // SEQ 6. 조회수 증가 (Redis Write Back Pattern)
        increaseViewCount(userPrincipal, archiveId, request);

        // SEQ 7. 배너 데이터 조회
        String bannerUrl = (archive.getBannerFile() != null)
                ? FileUrlUtils.buildCdnUrl(archive.getBannerFile().getS3ObjectKey())
                : null;

        // SEQ 8. 좋아요 여부 조회
        boolean isLiked = (viewerId != null) && likeRedisService.isLiked(
                ViewLikeDomain.ARCHIVE,
                archiveId,
                viewerId,
                () -> likeRepository.findAllUserIdsByArchiveId(archiveId),
                () -> {}
        );

        // Response: viewCount는 Stats에서, likeCount는 RealTime Table에서
        return ArchiveDto.Response.of(
                archive, bannerUrl, stats.getViewCount(), realTimeLikeCount, isLiked, isOwner
        );
    }

    @Transactional
    public ArchiveDto.Response updateArchive(UserPrincipal user, Long archiveId, ArchiveDto.UpdateRequest request) {
        // SEQ 1. Archive 조회
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유자 검증
        archiveGuard.checkOwner(archive.getUser().getId(), user);

        // SEQ 3. 기본 정보 수정
        archive.update(request); // 여기서 bannerUrl 은 처리하지 않음

        // SEQ 4. 배너 수정
        String bannerUrl = updateBannerImage(archive, request.getBannerImageId(), user.getUserId());

        // SEQ 5. 공개 범위(Visibility) 변경 시 Stats 테이블 동기화 + 캐시 무효화
        if (request.getVisibility() != null) {
            archiveStatsRepository.syncVisibility(archive.getId(), request.getVisibility());
            paginationIdCacheService.evictByPrefix("archive");
        }

        // SEQ 6. 리턴용 조회
        ArchiveStats stats = archiveStatsRepository.findById(archiveId)
                .orElse(ArchiveStats.create(archive));

        Long realTimeLikeCount = likeRedisService.getCount(
                ViewLikeDomain.ARCHIVE,
                archiveId,
                () -> likeRepository.findAllUserIdsByArchiveId(archiveId),
                () -> {}
        );

        boolean isLiked = likeRedisService.isLiked(
                ViewLikeDomain.ARCHIVE,
                archiveId,
                user.getUserId(),
                () -> likeRepository.findAllUserIdsByArchiveId(archiveId),
                () -> {}
        );

        return ArchiveDto.Response.of(
                archive, bannerUrl, stats.getViewCount(), realTimeLikeCount, isLiked, true
        );
    }

    @Transactional
    public void deleteArchive(UserPrincipal user, Long archiveId) {
        // SEQ 1. Archive 조회
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유자 검증
        archiveGuard.checkOwner(archive.getUser().getId(), user);

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

        // Step 2. 명시적 삭제 - 통계 데이터
        likeRepository.deleteByArchiveId(archiveId);
        archiveStatsRepository.deleteById(archiveId);

        // Step 3. Root 삭제
        // Cascade -> Sub Domain 삭제: DiaryBook, GalleryBook, TicketBook, RepostBook, Banner
        archiveRepository.delete(archive);

        // Step 4. Redis 캐시 삭제 (좋아요 + 페이지네이션 COUNT)
        likeRedisService.deleteLikeData(ViewLikeDomain.ARCHIVE, archiveId);
        paginationCountCacheService.evictByPrefix("archive");
        paginationIdCacheService.evictByPrefix("archive");

        log.info("🟢 Archive Delete Completed.");
    }

    @ExecutionTime
    @Transactional(readOnly = true)
    public PageDto.PageListResponse<ArchiveDto.ArchivePageResponse> getGlobalFeed(ArchiveDto.ArchivePageRequest request) {
        // 무조건 PUBLIC & 전체 유저 대상
        Page<ArchiveDto.ArchivePageResponse> page = archiveQueryRepository.searchArchiveFeed(
                null, // filterUserId
                List.of(Visibility.PUBLIC),
                request.toPageable()
        );

        PageUtils.validatePageRange(page);

        String title = "hotScore".equals(request.getSort()) ? "지금 핫한 피드" : "최신 아카이브 피드";

        return PageDto.PageListResponse.of(title, page);
    }

    @ExecutionTime
    @Transactional(readOnly = true)
    public PageDto.PageListResponse<ArchiveDto.ArchivePageResponse> getUserArchives(
            UserPrincipal userPrincipal,
            Long targetUserId,
            ArchiveDto.ArchivePageRequest request
    ) {
        List<Visibility> visibilities;
        String pageTitle;

        // 본인 확인
        if (userPrincipal != null && userPrincipal.getUserId().equals(targetUserId)) {
            visibilities = List.of(Visibility.PUBLIC, Visibility.RESTRICTED, Visibility.PRIVATE);
            pageTitle = "마이 아카이브";
        } else {
            // 친구 확인
            Long viewerId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
            boolean isFriend = archiveGuard.isFriend(viewerId, targetUserId);
            visibilities = isFriend
                    ? List.of(Visibility.PUBLIC, Visibility.RESTRICTED)
                    : List.of(Visibility.PUBLIC);

            // 닉네임 조회 (Optional, UX용)
            String nickname = userRepository.findById(targetUserId)
                    .map(User::getNickname)
                    .orElse("알 수 없는 사용자");
            pageTitle = nickname + "님의 아카이브";
        }

        Page<ArchiveDto.ArchivePageResponse> page = archiveQueryRepository.searchArchiveFeed(
                targetUserId,
                visibilities,
                request.toPageable()
        );

        PageUtils.validatePageRange(page);

        return PageDto.PageListResponse.of(pageTitle, page);
    }

    /**
     * 아카이브 좋아요 토글 (Redis + RabbitMQ)
     */
    public ArchiveDto.LikeResponse toggleLike(UserPrincipal userPrincipal, Long archiveId) {
        // 1. 아카이브 존재 확인 (불필요한 Redis 연산 방지)
        // if (!archiveRepository.existsById(archiveId)) {
        //     throw new RestException(ErrorCode.ARCHIVE_NOT_FOUND);
        // } -> 이거 하나 때문에 성능이 폭망함 (Bottle neck Point) -> 아니 애초에 게시글을 들어와서 좋아요를 누르겠지....

        // 2. Redis Toggle 수행 (Lua Script)
        boolean isLiked = likeRedisService.toggleLike(
                ViewLikeDomain.ARCHIVE,
                archiveId,
                userPrincipal.getUserId(),
                () -> likeRepository.findAllUserIdsByArchiveId(archiveId),
                () -> { // Lazy Validator: 필요할 때만 DB 조회
                    if (!archiveRepository.existsById(archiveId)) {
                        throw new RestException(ErrorCode.ARCHIVE_NOT_FOUND);
                    }
                }
        );

        // 3. 변경된 실시간 카운트 조회
        Long realTimeLikeCount = likeRedisService.getCount(
                ViewLikeDomain.ARCHIVE,
                archiveId,
                () -> likeRepository.findAllUserIdsByArchiveId(archiveId),
                () -> { // Lazy Validator: 필요할 때만 DB 조회
                    if (!archiveRepository.existsById(archiveId)) {
                        throw new RestException(ErrorCode.ARCHIVE_NOT_FOUND);
                    }
                }
        );

        return ArchiveDto.LikeResponse.builder()
                .archiveId(archiveId)
                .isLiked(isLiked)
                .likeCount(realTimeLikeCount)
                .build();
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

    private String updateBannerImage(Archive archive, Long newFileId, Long userId) {
        if (newFileId == null) {
            return archive.getBannerFile() != null
                    ? FileUrlUtils.buildCdnUrl(archive.getBannerFile().getS3ObjectKey())
                    : null;
        }

        if (newFileId == -1L) {
            // 삭제 요청: 기존 파일 연결 해제
            archive.updateBanner(null); // 실제 S3 삭제는 배치 처리로 위임 (Lazy Deletion)
            return null;
        } else {
            // 변경 요청

            File newBanner = fileService.validateFileOwner(newFileId, userId);
            archive.updateBanner(newBanner);
            return FileUrlUtils.buildCdnUrl(newBanner.getS3ObjectKey());
        }
    }

    private void increaseViewCount(UserPrincipal userPrincipal, Long archiveId, HttpServletRequest request) {
        if (request == null) return;

        Long userId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
        String clientIp = ClientUtils.getClientIp(request);

        redisViewService.incrementViewCount(ViewLikeDomain.ARCHIVE, archiveId, userId, clientIp);
    }
}