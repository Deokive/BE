package com.depth.deokive.domain.user.controller;

import com.depth.deokive.domain.user.dto.UserDto;
import com.depth.deokive.domain.user.service.UserService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "사용자 정보 처리 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "현재 로그인된 사용자의 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "내 정보 조회 성공")
    public UserDto.UserResponse retrieve(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return userService.retrieve(userPrincipal);
    }

    @PatchMapping("/me")
    @Operation(summary = "내 정보 수정", description = "현재 로그인된 사용자의 정보를 수정합니다.")
    @ApiResponse(responseCode = "200", description = "내 정보 수정 성공")
    public UserDto.UserResponse update(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UserDto.UserUpdateRequest request
    ) {
        return userService.update(userPrincipal, request);
    }
}
