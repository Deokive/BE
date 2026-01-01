package com.depth.deokive.domain.diary.controller;

import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.service.DiaryService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/diary")
@Tag(name = "Diary", description = "일기(다이어리) 관리 API")
public class DiaryController {

    private final DiaryService diaryService;

    @PostMapping("/{archiveId}")
    @Operation(summary = "일기 작성", description = "특정 아카이브에 일기를 작성합니다.")
    public ResponseEntity<DiaryDto.Response> createDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @RequestBody DiaryDto.CreateRequest request
    ) {
        DiaryDto.Response response = diaryService.createDiary(userPrincipal, archiveId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{diaryId}")
    @Operation(summary = "일기 상세 조회", description = "일기 상세 내용을 조회합니다. (공개 범위에 따라 접근이 제한될 수 있음)")
    public ResponseEntity<DiaryDto.Response> getDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long diaryId
    ) {
        DiaryDto.Response response = diaryService.retrieveDiary(userPrincipal, diaryId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{diaryId}")
    @Operation(summary = "일기 수정")
    public ResponseEntity<DiaryDto.Response> updateDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long diaryId,
            @Valid @RequestBody DiaryDto.UpdateRequest request
    ) {
        DiaryDto.Response response = diaryService.updateDiary(userPrincipal, diaryId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{diaryId}")
    @Operation(summary = "일기 삭제")
    public ResponseEntity<Void> deleteDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long diaryId
    ) {
        diaryService.deleteDiary(userPrincipal, diaryId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{archiveId}")
    @Operation(summary = "다이어리북 제목 수정", description = "다이어리북(폴더)의 제목을 수정합니다.")
    public ResponseEntity<DiaryDto.UpdateBookTitleResponse> updateDiaryBookTitle(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @RequestBody DiaryDto.UpdateBookTitleRequest request
    ) {
        DiaryDto.UpdateBookTitleResponse response = diaryService.updateDiaryBookTitle(userPrincipal, archiveId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/book/{archiveId}")
    @Operation(summary = "다이어리 목록 조회 (페이지네이션)", description = "아카이브 내의 다이어리 목록을 조회합니다.")
    public ResponseEntity<DiaryDto.PageListResponse> getDiaryFeed(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @ModelAttribute DiaryDto.DiaryPageRequest pageRequest
    ) {
        DiaryDto.PageListResponse response = diaryService.getDiaries(userPrincipal, archiveId, pageRequest);
        return ResponseEntity.ok(response);
    }
}