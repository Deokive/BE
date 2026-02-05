package com.depth.deokive.system.metadata.strategy;

import com.depth.deokive.common.util.FileUrlUtils;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.post.dto.MetadataProvider;
import com.depth.deokive.domain.post.dto.OgMetadata;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalArchiveMetadataProvider implements MetadataProvider {

    private final ArchiveRepository archiveRepository;

    @Value("${app.front-base-url}")
    private String frontBaseUrl;

    private Pattern pattern;

    @PostConstruct
    public void init() {
        // 여러 URL에서 도메인 패턴 추출
        String domainPattern = extractDomainPatternFromUrls(frontBaseUrl);
        String patternString = "^https?://(" + domainPattern + ").*/(?:feed|archive|archives)/(\\d+).*";
        this.pattern = Pattern.compile(patternString);
    }

    @Override
    public boolean supports(String url) {
        if (url == null) return false;
        return pattern.matcher(url).matches();
    }

    @Override
    @Transactional(readOnly = true)
    public OgMetadata extract(String url) {
        Long archiveId = extractId(url);

        // Fetch Join이 적용된 findByIdWithUser 활용 권장 (작성자 닉네임 활용 가능)
        Archive archive = archiveRepository.findByIdWithUser(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // 썸네일 URL 조합 (Archive 엔티티의 thumbnailKey 활용)
        String thumbnailUrl = null;
        if (archive.getThumbnailKey() != null) {
            thumbnailUrl = FileUrlUtils.buildCdnUrl(archive.getThumbnailKey());
        }

        // OgMetadata 반환
        return OgMetadata.builder()
                .title("[Deokive Archive] " + archive.getTitle())
                .description(generateDescription(archive))
                .imageUrl(thumbnailUrl)
                .build();
    }

    private Long extractId(String url) {
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(2));
            } catch (NumberFormatException e) {
                throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "Invalid Archive ID");
            }
        }
        throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "Invalid Internal Archive URL");
    }

    private String generateDescription(Archive archive) {
        String nickname = archive.getUser().getNickname();
        String badge = archive.getBadge().getDescription();
        return String.format("%s님의 아카이브 | 등급: %s | Deokive에서 기록된 덕질의 순간", nickname, badge);
    }

    /**
     * 여러 URL(콤마로 구분)에서 도메인 패턴을 추출하여 정규식 패턴으로 변환합니다.
     */
    private String extractDomainPatternFromUrls(String urls) {
        if (urls == null || urls.trim().isEmpty()) {
            return "localhost(?::\\d+)?|127\\.0\\.0\\.1";
        }

        List<String> domainPatterns = new ArrayList<>();
        
        // 기본값으로 127.0.0.1 추가
        domainPatterns.add("127\\.0\\.0\\.1");

        // 콤마로 구분된 URL들을 파싱
        String[] urlArray = urls.split(",");
        boolean hasLocalhost = false;

        for (String url : urlArray) {
            url = url.trim();
            if (url.isEmpty()) continue;

            try {
                // 프로토콜 제거 (http://, https://)
                String hostAndPort = url.replaceFirst("^https?://", "");
                
                // 호스트와 포트 분리
                String host;
                
                if (hostAndPort.contains(":")) {
                    int colonIndex = hostAndPort.indexOf(':');
                    host = hostAndPort.substring(0, colonIndex);
                } else {
                    host = hostAndPort;
                    // 포트가 없으면 기본 포트 (80, 443)이므로 선택적으로 처리
                }

                // localhost 또는 127.0.0.1인 경우
                if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
                    hasLocalhost = true;
                    // localhost는 포트 번호를 선택적으로 처리
                    continue; // 이미 127.0.0.1이 추가되어 있고, localhost는 아래에서 처리
                } else {
                    // 일반 도메인인 경우 점(.)을 이스케이프
                    String escapedHost = host.replace(".", "\\.");
                    domainPatterns.add(escapedHost);
                }
            } catch (Exception e) {
                log.warn("⚠️ [Internal Archive Provider] URL 파싱 실패: {}", url, e);
            }
        }

        // localhost가 있으면 포트 선택적 패턴 추가
        if (hasLocalhost) {
            domainPatterns.add(0, "localhost(?::\\d+)?");
        }

        return String.join("|", domainPatterns);
    }
}