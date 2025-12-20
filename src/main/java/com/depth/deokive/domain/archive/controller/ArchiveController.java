package com.depth.deokive.domain.archive.controller;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.service.ArchiveService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/archives")
@RequiredArgsConstructor
@Tag(name = "Archive", description = "아카이브 API")
public class ArchiveController {

    private final ArchiveService archiveService;

    @GetMapping("/me")
    @Operation(summary = "내 아카이브 목록 조회")
    public ResponseEntity<ArchiveDto.PageListResponse> getMyArchives(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @ModelAttribute ArchiveDto.ArchivePageRequest pageRequest
    ) {
        ArchiveDto.PageListResponse response =
                archiveService.getMyArchives(userPrincipal.getUserId(), pageRequest.toPageable());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/friend/{friendId}")
    @Operation(summary = "친구 아카이브 목록 조회")
    public ResponseEntity<ArchiveDto.PageListResponse> getFriendArchives(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId,
            @Valid @ModelAttribute ArchiveDto.ArchivePageRequest pageRequest
    ) {
        ArchiveDto.PageListResponse response =
                archiveService.getFriendArchives(userPrincipal.getUserId(), friendId, pageRequest.toPageable());

        return ResponseEntity.ok(response);
    }
}