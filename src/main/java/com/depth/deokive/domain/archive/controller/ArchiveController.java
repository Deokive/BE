package com.depth.deokive.domain.archive.controller;

import com.depth.deokive.domain.archive.dto.ArchiveMeResponseDto;
import com.depth.deokive.domain.archive.dto.CustomPageResponse;
import com.depth.deokive.domain.archive.service.ArchiveService;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("api/v1/archives")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    @GetMapping("/me")
    public ResponseEntity<CustomPageResponse<ArchiveMeResponseDto>> getMyArchive(
            @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal userPrincipal
            ) {
        // Constraints 로그인 유저 체크
        if(userPrincipal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Long userId = userPrincipal.getUserId();

        return ResponseEntity.ok(archiveService.getMyArchiveList(pageable, userId));
    }
}
