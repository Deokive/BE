package com.depth.deokive.system.metadata.strategy;

import com.depth.deokive.common.util.FileUrlUtils;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.metadata.dto.OEmbedDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ArchiveOEmbedStrategy implements OEmbedContentStrategy {

    private final ArchiveRepository archiveRepository;

    @Value("${app.front-base-url}")
    private String frontBaseUrl;

    // 패턴: /feed/{id}
    private static final Pattern PATTERN = Pattern.compile(".*/feed/(\\d+).*");

    @Override
    public boolean supports(String url) {
        if (url == null) return false;
        return PATTERN.matcher(url).matches();
    }

    @Override
    public OEmbedDto createOEmbed(String url) {
        Long id = extractId(url);

        Archive archive = archiveRepository.findById(id)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // Archive의 배너 이미지를 썸네일로 사용
        String thumbnailUrl = null;
        if (archive.getBannerFile() != null) {
            thumbnailUrl = FileUrlUtils.buildCdnUrl(archive.getBannerFile().getS3ObjectKey());
        }

        return OEmbedDto.builder()
                .title(archive.getTitle()) // 아카이브 제목
                .providerName("Deokive")
                .providerUrl(frontBaseUrl)
                .thumbnailUrl(thumbnailUrl)
                .thumbnailWidth(600)
                .thumbnailHeight(315)
                .build();
    }

    private Long extractId(String url) {
        Matcher matcher = PATTERN.matcher(url);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "Invalid Archive URL format");
    }
}
