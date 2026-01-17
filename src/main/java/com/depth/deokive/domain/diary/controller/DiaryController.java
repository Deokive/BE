package com.depth.deokive.domain.diary.controller;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.service.DiaryService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/diary")
@Tag(name = "Diary", description = "일기(다이어리) 관리 API")
public class DiaryController {

    private final DiaryService diaryService;

    @PostMapping("/{archiveId}")
    @Operation(summary = "일기 작성", description = "특정 아카이브에 일기를 작성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "일기 작성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "작성 권한 없음 (아카이브 소유자가 아님) 또는 파일 접근 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브 또는 파일입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "일기 조회 성공"),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 일기 또는 아카이브 접근 불가)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일기입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DiaryDto.Response> getDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long diaryId
    ) {
        DiaryDto.Response response = diaryService.retrieveDiary(userPrincipal, diaryId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{diaryId}")
    @Operation(summary = "일기 수정")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "일기 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (작성자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일기 또는 파일입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "일기 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (작성자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일기입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long diaryId
    ) {
        diaryService.deleteDiary(userPrincipal, diaryId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/book/{archiveId}")
    @Operation(summary = "다이어리북 제목 수정", description = "다이어리북(폴더)의 제목을 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "다이어리북 제목 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (제목 누락 등)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (아카이브 소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 요청 (페이지 범위 초과)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 아카이브)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageDto.PageListResponse<DiaryDto.DiaryPageResponse>> getDiaryFeed(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @ModelAttribute DiaryDto.DiaryPageRequest pageRequest
    ) {
        return ResponseEntity.ok(diaryService.getDiaries(userPrincipal, archiveId, pageRequest));
    }
}