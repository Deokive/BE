package com.depth.deokive.system.metadata.strategy;

import com.depth.deokive.domain.post.dto.MetadataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MetadataProviderFactory {

    private final List<MetadataProvider> providers;
    private final GenericJsoupProvider genericProvider; // Fallback

    public MetadataProvider getProvider(String url) {
        // GenericJsoupProvider를 제외한 리스트 중에서 매칭되는 것 탐색
        return providers.stream()
                .filter(p -> p != genericProvider)
                .filter(p -> p.supports(url))
                .findFirst()
                .orElse(genericProvider); // 매칭되는게 없으면 Generic 반환
    }
}