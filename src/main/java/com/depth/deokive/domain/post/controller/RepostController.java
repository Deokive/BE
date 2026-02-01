package com.depth.deokive.domain.post.controller;

import com.depth.deokive.domain.post.dto.RepostDto;
import com.depth.deokive.domain.post.service.RepostService;
import com.depth.deokive.system.exception.dto.ErrorResponse;
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
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/repost")
@Tag(name = "Repost", description = "리포스트(스크랩) 관리 API")
public class RepostController {

    private final RepostService repostService;

    @PostMapping("/tabs/{archiveId}")
    @RateLimit(type = RateLimitType.USER, capacity = 30, refillTokens = 30, refillPeriodSeconds = 3600)
    @Operation(summary = "리포스트 탭 생성", description = "최대 10개까지 생성 가능")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "리포스트 탭 생성 성공"),
            @ApiResponse(responseCode = "403", description = "생성 권한 없음 (아카이브 소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"ARCHIVE_NOT_FOUND\", \"message\": \"존재하지 않는 아카이브입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "탭 생성 개수 초과 (최대 10개)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"INTERNAL_SERVER_ERROR\", \"error\": \"REPOST_TAB_LIMIT_EXCEED\", \"message\": \"가능한 리포스트 탭 갯수를 초과했습니다.\"}")))
    })
    public ResponseEntity<RepostDto.TabResponse> createTab(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repostService.createRepostTab(user, archiveId));
    }

    @PatchMapping("/tabs/{tabId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "리포스트 탭 제목 수정")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "리포스트 탭 제목 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (제목 누락 등)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"BAD_REQUEST\", \"error\": \"GLOBAL_INVALID_PARAMETER\", \"message\": \"유효성 검사 실패: 필수 필드가 누락되었습니다.\"}"))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 탭입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"REPOST_TAB_NOT_FOUND\", \"message\": \"존재하지 않는 리포스트 탭입니다.\"}")))
    })
    public ResponseEntity<RepostDto.TabResponse> updateTab(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long tabId,
            @Valid @RequestBody RepostDto.UpdateTabRequest request
    ) {
        return ResponseEntity.ok(repostService.updateRepostTab(user, tabId, request));
    }

    @DeleteMapping("/tabs/{tabId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "리포스트 탭 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "리포스트 탭 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 탭입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"REPOST_TAB_NOT_FOUND\", \"message\": \"존재하지 않는 리포스트 탭입니다.\"}")))
    })
    public ResponseEntity<Void> deleteTab(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long tabId
    ) {
        repostService.deleteRepostTab(user, tabId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tabId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(
            summary = "리포스트 생성",
            description = "외부 SNS URL을 내 보관함에 저장합니다. " +
                    "Open Graph 메타데이터(제목, 썸네일)를 추출하여 완전한 데이터로 응답합니다. " +
                    "응답 시간: 약 1~3초 (외부 URL 의존)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "리포스트 생성 성공 (썸네일 + 타이틀 포함)"),
            @ApiResponse(responseCode = "400", description = "잘못된 URL 형식",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"BAD_REQUEST\", \"error\": \"REPOST_INVALID_URL\", \"message\": \"유효하지 않은 URL입니다.\"}"))),
            @ApiResponse(responseCode = "403", description = "생성 권한 없음 (소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 탭입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"REPOST_TAB_NOT_FOUND\", \"message\": \"존재하지 않는 리포스트 탭입니다.\"}"))),
            @ApiResponse(responseCode = "408", description = "URL 요청 시간 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"REQUEST_TIMEOUT\", \"error\": \"REPOST_URL_TIMEOUT\", \"message\": \"URL 요청 시간이 초과되었습니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "이미 해당 탭에 저장된 URL입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"CONFLICT\", \"error\": \"REPOST_URL_DUPLICATED\", \"message\": \"이미 해당 탭에 저장된 URL입니다.\"}"))),
            @ApiResponse(responseCode = "503", description = "URL에 접근할 수 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"SERVICE_UNAVAILABLE\", \"error\": \"REPOST_URL_UNREACHABLE\", \"message\": \"URL에 접근할 수 없습니다.\"}")))
    })
    public ResponseEntity<RepostDto.Response> createRepost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long tabId,
            @Valid @RequestBody RepostDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)  // 201 Created
                .body(repostService.createRepost(user, tabId, request));
    }

    @PatchMapping("/{repostId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "리포스트 제목 수정", description = "내가 설정한 리포스트 제목만 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "리포스트 제목 수정 성공"),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리포스트입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"REPOST_NOT_FOUND\", \"message\": \"존재하지 않는 리포스트입니다.\"}")))
    })
    public ResponseEntity<RepostDto.Response> updateRepost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long repostId,
            @Valid @RequestBody RepostDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(repostService.updateRepost(user, repostId, request));
    }

    @DeleteMapping("/{repostId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "리포스트 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "리포스트 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리포스트입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"REPOST_NOT_FOUND\", \"message\": \"존재하지 않는 리포스트입니다.\"}")))
    })
    public ResponseEntity<Void> deleteRepost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long repostId
    ) {
        repostService.deleteRepost(user, repostId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{archiveId}")
    @RateLimit(type = RateLimitType.AUTO, capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @Operation(summary = "리포스트 목록 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "리포스트 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"BAD_REQUEST\", \"error\": \"PAGE_NOT_FOUND\", \"message\": \"존재하지 않는 페이지입니다.\"}"))),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 아카이브)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브 또는 탭입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"ARCHIVE_NOT_FOUND\", \"message\": \"존재하지 않는 아카이브입니다.\"}")))
    })
    public ResponseEntity<RepostDto.RepostListResponse> getRepost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("archiveId") Long archiveId,
            @ParameterObject @ModelAttribute @Valid RepostDto.RepostPageRequest request
    ) {
        Pageable pageable = request.toPageable();
        Long tabId = request.getTabId();
        RepostDto.RepostListResponse response = repostService.getReposts(userPrincipal, archiveId, tabId, pageable);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("repost-book/{archiveId}")
    @RateLimit(type = RateLimitType.USER, capacity = 30, refillTokens = 30, refillPeriodSeconds = 3600)
    @Operation(summary = "리포스트북 타이틀 수정")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "리포스트북 타이틀 수정 성공"),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"ARCHIVE_NOT_FOUND\", \"message\": \"존재하지 않는 아카이브입니다.\"}")))
    })
    public ResponseEntity<RepostDto.RepostBookUpdateResponse> updateRepostBook(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @RequestBody RepostDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(repostService.updateRepostBookTitle(userPrincipal, request, archiveId));
    }
}