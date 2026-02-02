package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.service.ArchiveGuard;
import com.depth.deokive.common.util.PageUtils;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.post.dto.RepostDto;
import com.depth.deokive.domain.post.entity.*;
import com.depth.deokive.domain.post.repository.*;
import com.depth.deokive.domain.post.util.OpenGraphExtractor;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RepostService {

    private final ArchiveGuard archiveGuard;
    private final RepostRepository repostRepository;
    private final RepostTabRepository repostTabRepository;
    private final RepostBookRepository repostBookRepository;

    private final RepostQueryRepository repostQueryRepository;
    private final ArchiveRepository archiveRepository;

    /**
     * Repost 생성 - 동기 OG 추출 (Plan A Only)
     * - 트랜잭션 밖에서 OG 추출 → DB 커넥션 점유 최소화
     * - 완전한 데이터로 1번만 저장 → 이중 Write 방지
     * - 201 Created 응답 (썸네일 + 타이틀 포함)
     */
    @ExecutionTime
    public RepostDto.Response createRepost(UserPrincipal userPrincipal, Long tabId, RepostDto.CreateRequest request) {
        // SEQ 1. 탭 조회 (READ ONLY - 트랜잭션 불필요)
        RepostTab tab = repostTabRepository.findById(tabId)
                .orElseThrow(() -> new RestException(ErrorCode.REPOST_TAB_NOT_FOUND));

        // SEQ 2. 소유권 확인
        archiveGuard.checkOwner(tab.getRepostBook().getArchive().getUser().getId(), userPrincipal);

        // SEQ 3. URL 유효성 검증 (SSRF 방어 포함)
        String url = request.getUrl();
        validateUrl(url);

        // SEQ 4. 중복 체크 (URL 기반)
        if (repostRepository.existsByRepostTabIdAndUrl(tabId, url)) {
            throw new RestException(ErrorCode.REPOST_URL_DUPLICATED);
        }

        // SEQ 5. OG 메타데이터 추출 (트랜잭션 밖에서 수행 - Plan A) ✅
        String title;
        String thumbnailUrl = null;

        try {
            OpenGraphExtractor.OgMetadata metadata = OpenGraphExtractor.extract(url);
            title = metadata.getTitle();
            thumbnailUrl = metadata.getImageUrl();

            // Fallback: 제목이 없으면 도메인 이름 사용
            if (title == null || title.isBlank()) {
                title = extractDomainName(url);
            }
        } catch (SocketTimeoutException e) {
            throw new RestException(ErrorCode.REPOST_URL_TIMEOUT);
        } catch (IOException e) {
            throw new RestException(ErrorCode.REPOST_URL_UNREACHABLE);
        }

        // SEQ 6. DB에 1번만 저장 (트랜잭션 최소화) ✅
        return saveRepost(tab, url, title, thumbnailUrl);
    }

    /**
     * Repost 저장 (트랜잭션 최소화)
     */
    @Transactional
    public RepostDto.Response saveRepost(RepostTab tab, String url, String title, String thumbnailUrl) {
        Repost repost = Repost.builder()
                .repostTab(tab)
                .url(url)
                .title(title)
                .thumbnailUrl(thumbnailUrl)
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
        archiveGuard.checkOwner(repost.getRepostTab().getRepostBook().getArchive().getUser().getId(), userPrincipal);

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
        archiveGuard.checkOwner(repost.getRepostTab().getRepostBook().getArchive().getUser().getId(), userPrincipal);

        // SEQ 3. Repost 삭제
        repostRepository.delete(repost);
    }

    @Transactional
    public RepostDto.TabResponse createRepostTab(UserPrincipal userPrincipal, Long archiveId) {
        // SEQ 1. 리포스트북 조회 (= Archive와 1:1)
        RepostBook book = repostBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유권 검증
        archiveGuard.checkOwner(book.getArchive().getUser().getId(), userPrincipal);

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
        archiveGuard.checkOwner(tab.getRepostBook().getArchive().getUser().getId(), userPrincipal);

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
        archiveGuard.checkOwner(tab.getRepostBook().getArchive().getUser().getId(), userPrincipal);

        // SEQ 3. 리포스트 탭 제거
        repostRepository.deleteAllByRepostTabId(tabId); // Bulk로 Repost 명시적 삭제 (성능을 위해)
        repostTabRepository.delete(tab);
    }

    @Transactional
    public RepostDto.RepostListResponse getReposts(UserPrincipal userPrincipal, Long archiveId, Long tabId, Pageable pageable) {
        // SEQ 1. 리포스트북 제목 조회
        RepostBook book = repostBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 공개 범위 검증
        archiveGuard.checkArchiveReadPermission(book.getArchive(), userPrincipal);

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
        Page<RepostDto.RepostElementResponse> page = repostQueryRepository.findByTabId(targetTabId, pageable);

        PageUtils.validatePageRange(page);

        List<RepostDto.TabResponse> tabDtos = tabs.stream()
                .map(RepostDto.TabResponse::of)
                .toList();

        // SEQ 6. 리턴
        return RepostDto.RepostListResponse.of(book.getTitle(), targetTabId, tabDtos, page);
    }

    @Transactional
    public RepostDto.RepostBookUpdateResponse updateRepostBookTitle(UserPrincipal userPrincipal, RepostDto.UpdateRequest request, Long archiveId){
        // SEQ 1. 아카이브 조회
        Archive archive = archiveRepository.findByIdWithUser(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유권 검증
        archiveGuard.checkOwner(archive.getUser().getId(), userPrincipal);

        // SEQ 3. 리포스트 북 타이틀 수정
        RepostBook repostBook = archive.getRepostBook();
        repostBook.updateTitle(request.getTitle()); // Dirty Checking

        return RepostDto.RepostBookUpdateResponse.of(repostBook);
    }

    // Helper methods

    /**
     * SSRF 방어를 포함한 URL 검증
     * - 프로토콜: http/https만 허용
     * - SSRF: localhost, 내부망 IP 차단
     * - @ 기법: UserInfo 포함 URL 차단
     * - 길이: 2048자 초과 차단 (DB 제약 준수)
     */
    private void validateUrl(String url) {
        // SEQ 1. 길이 검증 (DB VARCHAR(2048) 제약)
        if (url == null || url.length() > 2048) {
            throw new RestException(ErrorCode.REPOST_INVALID_URL);
        }

        // SEQ 2. 프로토콜 검증
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new RestException(ErrorCode.REPOST_INVALID_URL);
        }

        // SEQ 3. URI 파싱
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new RestException(ErrorCode.REPOST_INVALID_URL);
        }

        // SEQ 4. Host 존재 검증
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new RestException(ErrorCode.REPOST_INVALID_URL);
        }

        // SEQ 5. SSRF 방어 - localhost 차단
        String hostLower = host.toLowerCase();
        if (isLocalhost(hostLower)) {
            throw new RestException(ErrorCode.REPOST_INVALID_URL);
        }

        // SEQ 6. SSRF 방어 - 내부망 IP 차단
        if (isPrivateNetwork(hostLower)) {
            throw new RestException(ErrorCode.REPOST_INVALID_URL);
        }

        // SEQ 7. @ 기법 방어 - UserInfo 차단
        if (uri.getUserInfo() != null) {
            throw new RestException(ErrorCode.REPOST_INVALID_URL);
        }
    }

    /**
     * localhost 판별
     */
    private boolean isLocalhost(String host) {
        return host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("0.0.0.0")
                || host.equals("[::]")
                || host.equals("[::1]");
    }

    /**
     * 내부망 IP 판별 (RFC 1918 + Link-local)
     */
    private boolean isPrivateNetwork(String host) {
        return host.startsWith("192.168.")      // Class C private
                || host.startsWith("10.")        // Class A private
                || isRFC1918ClassB(host)         // 172.16.0.0/12
                || host.startsWith("169.254.");  // Link-local
    }

    /**
     * RFC 1918 Class B (172.16.0.0 ~ 172.31.255.255)
     */
    private boolean isRFC1918ClassB(String host) {
        if (!host.startsWith("172.")) {
            return false;
        }
        String[] parts = host.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int secondOctet = Integer.parseInt(parts[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String extractDomainName(String url) {
        try {
            return new URI(url).toURL().getHost();
        } catch (URISyntaxException | MalformedURLException e) {
            return "Unknown";
        }
    }
}