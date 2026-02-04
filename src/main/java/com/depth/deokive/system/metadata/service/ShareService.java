package com.depth.deokive.system.metadata.service;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.util.FileUrlUtils;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.metadata.dto.ShareMetadataDto;
import com.depth.deokive.system.security.util.FrontUrlResolver;
import com.depth.deokive.system.security.util.PropertiesParserUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공유 페이지 메타데이터 및 리다이렉트 URL 생성 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final PostRepository postRepository;
    private final ArchiveRepository archiveRepository;

    @Value("${app.front-base-url}")
    private String frontBaseUrlConfig;

    /**
     * 게시글 공유 메타데이터 및 리다이렉트 URL 생성
     */
    @Transactional(readOnly = true)
    public ShareMetadataDto getPostShareMetadata(Long postId, HttpServletRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        // 메타데이터 설정
        String title = post.getTitle();
        String description = post.getContent().length() > 100
                ? post.getContent().substring(0, 100) + "..."
                : post.getContent();

        String imageUrl = post.getThumbnailKey() != null
                ? FileUrlUtils.buildCdnUrl(post.getThumbnailKey())
                : "";

        // 요청에 맞는 프론트엔드 URL 선택
        String frontBaseUrl = resolveFrontBaseUrl(request);
        String redirectUrl = frontBaseUrl + "/community/" + postId;

        return ShareMetadataDto.builder()
                .ogTitle(title)
                .ogDescription(description)
                .ogImage(imageUrl)
                .redirectUrl(redirectUrl)
                .build();
    }

    /**
     * 아카이브 공유 메타데이터 및 리다이렉트 URL 생성
     */
    @Transactional(readOnly = true)
    public ShareMetadataDto getArchiveShareMetadata(Long archiveId, HttpServletRequest request) {
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // 비공개 아카이브는 공유 불가
        if (archive.getVisibility() == Visibility.PRIVATE) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }

        String title = archive.getTitle();
        String description = archive.getUser().getNickname() + "님의 아카이브를 구경해보세요!";

        String imageUrl = archive.getBannerFile() != null
                ? FileUrlUtils.buildCdnUrl(archive.getBannerFile().getS3ObjectKey())
                : "";

        // 요청에 맞는 프론트엔드 URL 선택
        String frontBaseUrl = resolveFrontBaseUrl(request);
        String redirectUrl = frontBaseUrl + "/feed/" + archiveId;

        return ShareMetadataDto.builder()
                .ogTitle(title)
                .ogDescription(description)
                .ogImage(imageUrl)
                .redirectUrl(redirectUrl)
                .build();
    }

    /**
     * 요청의 Origin/Referer를 확인하여 적절한 프론트엔드 base URL을 선택합니다.
     * 매칭되지 않으면 첫 번째 URL을 기본값으로 사용합니다.
     */
    private String resolveFrontBaseUrl(HttpServletRequest request) {
        List<String> allowedBaseUrls = PropertiesParserUtils.propertiesParser(frontBaseUrlConfig);
        
        if (allowedBaseUrls.isEmpty()) {
            log.warn("⚠️ [ShareService] 허용된 프론트엔드 URL이 없습니다. 설정을 확인하세요.");
            throw new RestException(ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR, "프론트엔드 URL 설정이 없습니다.");
        }

        return FrontUrlResolver.resolveUrl(request, allowedBaseUrls, allowedBaseUrls.get(0));
    }
}
