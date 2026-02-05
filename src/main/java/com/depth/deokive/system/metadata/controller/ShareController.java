package com.depth.deokive.system.metadata.controller;

import com.depth.deokive.system.metadata.dto.ShareMetadataDto;
import com.depth.deokive.system.metadata.service.ShareService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 공유 페이지 컨트롤러
 * [봇] OG 태그가 포함된 HTML 파싱
 * [사람] JS를 통해 프론트엔드 상세 페이지로 리다이렉트
 */
@Slf4j
@Controller // @RestController 아님 (View 반환)
@RequestMapping("/share")
@RequiredArgsConstructor
@Hidden // Swagger 문서 제외 (브라우저/봇 전용)
public class ShareController {

    private final ShareService shareService;

    /**
     * 게시글 공유 페이지
     */
    @GetMapping("/posts/{postId}")
    public String sharePost(@PathVariable Long postId, Model model, HttpServletRequest request) {
        ShareMetadataDto metadata = shareService.getPostShareMetadata(postId, request);

        model.addAttribute("ogTitle", metadata.getOgTitle());
        model.addAttribute("ogDescription", metadata.getOgDescription());
        model.addAttribute("ogImage", metadata.getOgImage());
        model.addAttribute("redirectUrl", metadata.getRedirectUrl());

        return "share"; // resources/templates/share.html
    }

    /**
     * 아카이브 공유 페이지
     */
    @GetMapping("/archives/{archiveId}")
    public String shareArchive(@PathVariable Long archiveId, Model model, HttpServletRequest request) {
        ShareMetadataDto metadata = shareService.getArchiveShareMetadata(archiveId, request);

        model.addAttribute("ogTitle", metadata.getOgTitle());
        model.addAttribute("ogDescription", metadata.getOgDescription());
        model.addAttribute("ogImage", metadata.getOgImage());
        model.addAttribute("redirectUrl", metadata.getRedirectUrl());

        return "share";
    }
}