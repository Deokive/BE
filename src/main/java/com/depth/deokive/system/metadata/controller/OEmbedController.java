package com.depth.deokive.system.metadata.controller;

import com.depth.deokive.system.metadata.dto.OEmbedDto;
import com.depth.deokive.system.metadata.service.OEmbedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oembed")
@RequiredArgsConstructor
@Tag(name = "Share", description = "공유 및 미리보기(oEmbed) API")
public class OEmbedController {

    private final OEmbedService oEmbedService;

    @Operation(summary = "oEmbed 데이터 제공", description = "외부 플랫폼(디스코드 등)에서 Deokive 링크의 메타데이터를 요청할 때 사용합니다.")
    @GetMapping
    public ResponseEntity<OEmbedDto> getOEmbed(
            @RequestParam("url") String url,
            @RequestParam(value = "format", defaultValue = "json") String format
    ) {
        return ResponseEntity.ok(oEmbedService.getOEmbedData(url));
    }
}