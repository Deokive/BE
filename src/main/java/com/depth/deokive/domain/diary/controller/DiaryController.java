package com.depth.deokive.domain.diary.controller;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.service.DiaryService;
import com.depth.deokive.system.ratelimit.annotation.RateLimit;
import com.depth.deokive.system.ratelimit.annotation.RateLimitType;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    @RateLimit(type = RateLimitType.USER, capacity = 50, refillTokens = 50, refillPeriodSeconds = 3600)
    @Operation(summary = "일기 작성", description = "특정 아카이브에 일기를 작성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "작성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (날짜 포맷, 색상 코드 오류 등)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"BAD_REQUEST\", \"error\": \"GLOBAL_INVALID_PARAMETER\", \"message\": \"올바른 HEX 컬러 코드가 아닙니다.\"}")
                    )),
            @ApiResponse(responseCode = "403", description = "권한 없음 (아카이브 주인이 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}")
                    )),
            @ApiResponse(responseCode = "404", description = "아카이브 또는 파일 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"ARCHIVE_NOT_FOUND\", \"message\": \"존재하지 않는 아카이브입니다.\"}")
                    ))
    })
    public ResponseEntity<DiaryDto.Response> createDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "다이어리북 ID (Archive ID와 동일)", example = "1") @PathVariable Long archiveId,
            @Valid @RequestBody DiaryDto.CreateRequest request
    ) {
        DiaryDto.Response response = diaryService.createDiary(userPrincipal, archiveId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{diaryId}")
    @RateLimit(type = RateLimitType.AUTO, capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @Operation(summary = "일기 상세 조회", description = "일기 상세 내용을 조회합니다. (공개 범위에 따라 접근이 제한될 수 있음)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 일기 등)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}")
                    )),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일기",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"DIARY_NOT_FOUND\", \"message\": \"존재하지 않는 일기입니다.\"}")
                    ))
    })
    public ResponseEntity<DiaryDto.Response> getDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "조회할 일기 ID", example = "15") @PathVariable Long diaryId
    ) {
        DiaryDto.Response response = diaryService.retrieveDiary(userPrincipal, diaryId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{diaryId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "일기 수정")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}")
                    )),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일기",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"DIARY_NOT_FOUND\", \"message\": \"존재하지 않는 일기입니다.\"}")
                    ))
    })
    public ResponseEntity<DiaryDto.Response> updateDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "수정할 일기 ID", example = "15") @PathVariable Long diaryId,
            @Valid @RequestBody DiaryDto.UpdateRequest request
    ) {
        DiaryDto.Response response = diaryService.updateDiary(userPrincipal, diaryId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{diaryId}")
    @RateLimit(type = RateLimitType.USER, capacity = 50, refillTokens = 50, refillPeriodSeconds = 3600)
    @Operation(summary = "일기 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "일기 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (작성자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일기입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "삭제할 일기 ID", example = "15") @PathVariable Long diaryId
    ) {
        diaryService.deleteDiary(userPrincipal, diaryId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/book/{archiveId}")
    @RateLimit(type = RateLimitType.USER, capacity = 30, refillTokens = 30, refillPeriodSeconds = 3600)
    @Operation(summary = "다이어리북 제목 수정", description = "다이어리북(폴더)의 제목을 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "다이어리북 제목 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (제목 누락 등)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (아카이브 소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DiaryDto.UpdateBookTitleResponse> updateDiaryBookTitle(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "아카이브 ID", example = "1") @PathVariable Long archiveId,
            @Valid @RequestBody DiaryDto.UpdateBookTitleRequest request
    ) {
        DiaryDto.UpdateBookTitleResponse response = diaryService.updateDiaryBookTitle(userPrincipal, archiveId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/book/{archiveId}")
    @RateLimit(type = RateLimitType.AUTO, capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @Operation(summary = "다이어리 목록 조회 (페이지네이션)", description = "아카이브 내의 다이어리 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 요청 (페이지 범위 초과)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 아카이브)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageDto.PageListResponse<DiaryDto.DiaryPageResponse>> getDiaryFeed(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "아카이브 ID", example = "1") @PathVariable Long archiveId,
            @Valid @ModelAttribute DiaryDto.DiaryPageRequest pageRequest
    ) {
        return ResponseEntity.ok(diaryService.getDiaries(userPrincipal, archiveId, pageRequest));
    }
}