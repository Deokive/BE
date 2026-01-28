package com.depth.deokive.domain.user.controller;

import com.depth.deokive.domain.user.dto.UserDto;
import com.depth.deokive.domain.user.service.UserService;
import com.depth.deokive.system.ratelimit.annotation.RateLimit;
import com.depth.deokive.system.ratelimit.annotation.RateLimitType;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Hidden;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.depth.deokive.system.exception.dto.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "사용자 정보 처리 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @RateLimit(type = RateLimitType.USER, capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @Operation(summary = "내 정보 조회", description = "현재 로그인된 사용자의 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "내 정보 조회 성공"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 사용자입니다.\"}")))
    })
    public UserDto.UserResponse retrieve(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return userService.retrieveMe(userPrincipal);
    }

    @PatchMapping("/me")
    @RateLimit(type = RateLimitType.USER, capacity = 20, refillTokens = 20, refillPeriodSeconds = 3600)
    @Operation(summary = "내 정보 수정", description = "현재 로그인된 사용자의 정보를 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "내 정보 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"BAD_REQUEST\", \"error\": \"GLOBAL_INVALID_PARAMETER\", \"message\": \"유효성 검사 실패: 필수 필드가 누락되었습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 사용자입니다.\"}")))
    })
    public UserDto.UserResponse update(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UserDto.UserUpdateRequest request
    ) {
        return userService.update(userPrincipal, request);
    }

    @GetMapping("/{userId}")
    @RateLimit(type = RateLimitType.AUTO, capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @Operation(summary = "유저 정보 조회", description = "사용자의 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "유저 정보 조회 성공"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 사용자입니다.\"}")))
    })
    public UserDto.UserResponse retrieve(@PathVariable Long userId) {
        return userService.retrieve(userId);
    }
}
