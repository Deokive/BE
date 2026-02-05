package com.depth.deokive.system.metadata.service;

import com.depth.deokive.system.metadata.dto.OEmbedDto;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.metadata.strategy.OEmbedContentStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OEmbedService {

    // Spring이 구현체들을 자동으로 리스트에 주입함
    private final List<OEmbedContentStrategy> strategies;

    @Transactional(readOnly = true)
    public OEmbedDto getOEmbedData(String url) {
        // 1. URL 디코딩
        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);

        // 2. 지원하는 전략 탐색 (Post? Archive?)
        return strategies.stream()
                .filter(strategy -> strategy.supports(decodedUrl))
                .findFirst()
                .map(strategy -> strategy.createOEmbed(decodedUrl))
                .orElseThrow(() -> new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "지원하지 않는 URL입니다."));
    }
}