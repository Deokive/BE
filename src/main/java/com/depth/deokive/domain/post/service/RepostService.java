package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.service.ArchiveGuard;
import com.depth.deokive.common.util.PageUtils;
import com.depth.deokive.common.util.TextUtils;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.post.dto.RepostDto;
import com.depth.deokive.domain.post.entity.*;
import com.depth.deokive.domain.post.entity.enums.RepostStatus;
import com.depth.deokive.domain.post.repository.*;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.dao.DataIntegrityViolationException;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepostService {

    private final ArchiveGuard archiveGuard;
    private final RepostRepository repostRepository;
    private final RepostTabRepository repostTabRepository;
    private final RepostBookRepository repostBookRepository;

    private final RepostQueryRepository repostQueryRepository;
    private final ArchiveRepository archiveRepository;

    private final RepostOgProducer repostOgProducer;

    /**
     * Repost 생성 - 비동기 OG 추출 (RabbitMQ)
     *
     * [처리 흐름]
     * 1. URL 검증 (SSRF 방어)
     * 2. PENDING 상태로 저장 (UNIQUE constraint가 중복 방어)
     * 3. RabbitMQ 메시지 발행 (비동기)
     * 4. 201 Created 즉시 응답
     * 5. Consumer가 백그라운드에서 OG 추출 (1.5초)
     */
    @ExecutionTime
    @Transactional
    public RepostDto.Response createRepost(UserPrincipal userPrincipal, Long tabId, RepostDto.CreateRequest request) {
        // SEQ 1. 탭 조회 (Fetch Join으로 N+1 방지)
        RepostTab tab = repostTabRepository.findByIdWithOwner(tabId)
                .orElseThrow(() -> new RestException(ErrorCode.REPOST_TAB_NOT_FOUND));

        // SEQ 2. 소유권 확인
        archiveGuard.checkOwner(tab.getRepostBook().getArchive().getUser().getId(), userPrincipal);

        // SEQ 3. URL 유효성 검증 (SSRF 방어 포함)
        String url = request.getUrl();
        validateUrl(url);

        // SEQ 4. URL 해시 생성 (SHA-256)
        String urlHash = generateUrlHash(url);

        // SEQ 5. PENDING 상태로 저장 (UNIQUE constraint가 중복 방어)

        String safeTitle = TextUtils.truncate(url, 255);

        Repost repost = Repost.builder()
                .repostTab(tab)
                .url(url)
                .urlHash(urlHash)
                .title(safeTitle)  // 임시로 URL을 title로 사용 (255자 제한)
                .thumbnailUrl(null)
                .status(RepostStatus.PENDING)
                .build();

        try {
            repost = repostRepository.save(repost);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE constraint violation (repost_tab_id, url) → 중복 URL
            throw new RestException(ErrorCode.REPOST_URL_DUPLICATED);
        }

        // SEQ 5. RabbitMQ 발행 (트랜잭션 커밋 후 실제 전송, SSE 알림용 userId 포함)
        repostOgProducer.requestOgExtraction(repost.getId(), userPrincipal.getUserId(), url);

        // SEQ 6. 즉시 201 Created 응답
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

    @ExecutionTime
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

    /**
     * URL을 SHA-256으로 해싱하여 고정 길이(64자) 해시값 생성
     * - Unique constraint에 사용
     * - 인덱스 성능 최적화 (고정 길이)
     */
    private String generateUrlHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}