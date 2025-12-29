package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.post.dto.RepostDto;
import com.depth.deokive.domain.post.entity.*;
import com.depth.deokive.domain.post.repository.*;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.depth.deokive.domain.user.entity.QUser.user;

@Service
@RequiredArgsConstructor
public class RepostService {

    private final RepostRepository repostRepository;
    private final RepostTabRepository repostTabRepository;
    private final RepostBookRepository repostBookRepository;

    private final PostRepository postRepository; // Post 정보 조회를 위한 Repository (검증 및 스냅샷 용도)
    private final PostFileMapRepository postFileMapRepository;
    private final RepostQueryRepository repostQueryRepository;
    private final FriendMapRepository friendMapRepository;

    @Transactional
    public RepostDto.Response createRepost(UserPrincipal userPrincipal, Long tabId, RepostDto.CreateRequest request) {
        // SEQ 1. 탭 조회
        RepostTab tab = repostTabRepository.findById(tabId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND)); // ErrorCode.TAB_NOT_FOUND 권장

        // SEQ 2. 소유권 확인
        validateTabOwner(tab, userPrincipal);

        // SEQ 3. 원본 게시글 확인
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        // SEQ 4. 중복 체크
        if (repostRepository.existsByRepostTabIdAndPostId(tabId, request.getPostId())) {
            throw new RestException(ErrorCode.REPOST_TAB_AND_POST_DUPLICATED);
        }

        // SEQ 5. 스냅샷 데이터 추출 (제목 & 썸네일)
        String titleSnapshot = post.getTitle(); // 생성 시점에선 자동으로 원본 게시글의 제목을 저장
        String thumbnailSnapshot = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId()).stream()
                .filter(map -> map.getMediaRole() == MediaRole.PREVIEW || map.getMediaRole() == MediaRole.CONTENT)
                .findFirst() // 첫 번째 이미지를 썸네일로 사용
                .map(map -> map.getFile().getFilePath())
                .orElse(null);

        // SEQ 6. 저장
        Repost repost = Repost.builder()
                .repostTab(tab)
                .postId(post.getId())
                .title(titleSnapshot)
                .thumbnailUrl(ThumbnailUtils.getSmallThumbnailUrl(thumbnailSnapshot)) // 썸네일 URL로 저장 (원본 게시글이 아니므로)
                .build();
        repostRepository.save(repost);

        return RepostDto.Response.of(repost);
    }

    @Transactional
    public RepostDto.Response updateRepost(UserPrincipal userPrincipal, Long repostId, RepostDto.UpdateRequest request) {
        // SEQ 1. 리포스트 조회
        Repost repost = repostRepository.findById(repostId)
                .orElseThrow(() -> new RestException(ErrorCode.REPOST_NOT_FOUND));

        // SEQ 2. 소유권 점검
        validateTabOwner(repost.getRepostTab(), userPrincipal);

        // SEQ 3. 타이틀 수정 (Repost 자체는 편집의 대상이 아님)
        repost.updateTitle(request.getTitle()); // Dirty Checking
        return RepostDto.Response.of(repost);
    }

    @Transactional
    public void deleteRepost(UserPrincipal userPrincipal, Long repostId) {
        // SEQ 1. 리포스트 조회
        Repost repost = repostRepository.findById(repostId)
                .orElseThrow(() -> new RestException(ErrorCode.REPOST_NOT_FOUND));

        // SEQ 2. 소유권 검증
        validateTabOwner(repost.getRepostTab(), userPrincipal);

        // SEQ 3. Repost 삭제
        repostRepository.delete(repost);
    }

    @Transactional
    public RepostDto.TabResponse createRepostTab(UserPrincipal userPrincipal, Long archiveId) {
        // SEQ 1. 리포스트북 조회 (= Archive와 1:1)
        RepostBook book = repostBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유권 검증
        validateBookOwner(book, userPrincipal);

        // SEQ 3. 10개 제한 체크
        long count = repostTabRepository.countByRepostBookId(archiveId);
        if (count >= 10) {
            throw new RestException(ErrorCode.REPOST_TAB_LIMIT_EXCEED);
        }

        // SEQ 4. 리포스트 탭 생성 및 저장
        RepostTab tab = RepostTab.builder()
                .repostBook(book)
                .title((count + 1) + "번째 탭") // UX 흐름 상 탭을 일단 생성 후 탭 이름 수정하는 경우가 많음 -> 기본 탭 이름 지정
                .build();

        repostTabRepository.save(tab);

        return RepostDto.TabResponse.of(tab);
    }

    @Transactional
    public RepostDto.TabResponse updateRepostTab(UserPrincipal userPrincipal, Long tabId, RepostDto.UpdateTabRequest request) {
        // SEQ 1. 리포스트 탭 조회
        RepostTab tab = repostTabRepository.findById(tabId)
                .orElseThrow(() -> new RestException(ErrorCode.REPOST_TAB_NOT_FOUND));

        // SEQ 2. 소유권 검증
        validateTabOwner(tab, userPrincipal);

        // SEQ 3. 리포스트 탭 타이틀 수정
        tab.updateTitle(request.getTitle());

        return RepostDto.TabResponse.of(tab);
    }

    @Transactional
    public void deleteRepostTab(UserPrincipal userPrincipal, Long tabId) {
        // SEQ 1. 리포스트 탭 조회
        RepostTab tab = repostTabRepository.findById(tabId)
                .orElseThrow(() -> new RestException(ErrorCode.REPOST_TAB_NOT_FOUND));

        // SEQ 2. 소유권 검증
        validateTabOwner(tab, userPrincipal);

        // SEQ 3. 리포스트 탭 제거
        repostRepository.deleteAllByRepostTabId(tabId); // Bulk로 Repost 명시적 삭제 (성능을 위해)
        repostTabRepository.delete(tab);
    }

    /**
     * 리포스트 목록 페이징 조회
     */
    @Transactional
    public RepostDto.RepostListResponse getReposts(UserPrincipal userPrincipal, Long archiveId, Long tabId, Pageable pageable) {
        // SEQ 1. 리포스트북 제목 조회
        RepostBook book = repostBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 공개 범위 검증
        validateArchiveAccess(userPrincipal, book.getArchive());

        // SEQ 3. 전체 탭 목록 조회
        List<RepostTab> tabs = repostTabRepository.findAllByRepostBookIdOrderByIdAsc(archiveId);

        // SEQ 4. 탭이 없는 경우 빈 페이지
        if(tabs.isEmpty()) {
            return RepostDto.RepostListResponse.of(book.getTitle(), null, List.of(), Page.empty(pageable));
        }

        // SEQ 5. tabId가 존재하는지 검증
        Long targetTabId;
        if (tabId == null) {
            targetTabId = tabs.get(0).getId(); // 없으면 첫 번째 탭
        } else {
            // 요청한 tabId가 조회된 tabs 목록에 포함되어 있는지 확인
            boolean isValidTab = tabs.stream().anyMatch(t -> t.getId().equals(tabId));
            if (!isValidTab) {
                // 존재하지 X -> 404 에러
                throw new RestException(ErrorCode.REPOST_TAB_NOT_FOUND);
            }
            targetTabId = tabId;
        }

        // SEQ 5. 페이지네이션 쿼리
        Page<RepostDto.Response> pageResult = repostQueryRepository.findByTabId(targetTabId, pageable);

        List<RepostDto.TabResponse> tabDtos = tabs.stream()
                .map(RepostDto.TabResponse::of)
                .toList();

        if (pageable.getPageNumber() > 0 && pageResult.isEmpty()) {
            throw new RestException(ErrorCode.DB_DATA_NOT_FOUND);
        }

        // SEQ 6. 리턴
        return RepostDto.RepostListResponse.of(book.getTitle(), targetTabId, tabDtos, pageResult);
    }

    // --- Helpers ---
    private void validateTabOwner(RepostTab tab, UserPrincipal user) {
        if (!tab.getRepostBook().getArchive().getUser().getId().equals(user.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void validateBookOwner(RepostBook book, UserPrincipal user) {
        if (!book.getArchive().getUser().getId().equals(user.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void validateArchiveAccess(UserPrincipal viewer, Archive archive) {
        Long ownerId = archive.getUser().getId();
        Long viewerId = (viewer != null) ? viewer.getUserId() : null;

        // 1. 주인은 무조건 통과
        if (ownerId.equals(viewerId)) {
            return;
        }

        Visibility visibility = archive.getVisibility();

        // 2. 비공개(PRIVATE) -> 주인이 아니면 차단
        if (visibility == Visibility.PRIVATE) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }

        // 3. 친구공개(RESTRICTED) -> 친구가 아니면 차단
        if (visibility == Visibility.RESTRICTED) {
            if (!isFriend(viewerId, ownerId)) {
                throw new RestException(ErrorCode.AUTH_FORBIDDEN);
            }
        }

        // 4. 전체공개(PUBLIC) -> 통과 (아무것도 안 함)
    }

    private boolean isFriend(Long viewerId, Long ownerId) {
        if (viewerId == null) return false; // 비로그인 유저는 친구일 수 없음

        return friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(
                viewerId,
                ownerId,
                FriendStatus.ACCEPTED
        );
    }
}