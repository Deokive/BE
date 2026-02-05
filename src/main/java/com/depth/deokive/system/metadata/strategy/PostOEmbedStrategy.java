package com.depth.deokive.system.metadata.strategy;

import com.depth.deokive.common.util.FileUrlUtils;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.repository.PostRepository;
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
public class PostOEmbedStrategy implements OEmbedContentStrategy {

    private final PostRepository postRepository;

    @Value("${app.front-base-url}")
    private String frontBaseUrl;

    // 지원하는 패턴: /community/{id}, /feed/{id}, /posts/{id} 모두 대응 -> (\d+)이 ID가 됨
    private static final Pattern PATTERN = Pattern.compile(".*/(community|posts)/(\\d+).*");

    @Override
    public boolean supports(String url) {
        if (url == null) return false;
        return PATTERN.matcher(url).matches();
    }

    @Override
    public OEmbedDto createOEmbed(String url) {
        Long id = extractId(url);

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        String thumbnailUrl = (post.getThumbnailKey() != null)
                ? FileUrlUtils.buildCdnUrl(post.getThumbnailKey())
                : null;

        return OEmbedDto.builder()
                .title(post.getTitle())
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
            return Long.parseLong(matcher.group(2)); // (\d+) 부분 추출
        }
        throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "Invalid Post URL format");
    }
}
