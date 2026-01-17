package com.depth.deokive.domain.auth.controller;

import com.depth.deokive.domain.auth.dto.AuthDto;
import com.depth.deokive.domain.auth.service.AuthService;
import com.depth.deokive.domain.auth.service.EmailService;
import com.depth.deokive.domain.user.dto.UserDto;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.depth.deokive.system.security.model.UserPrincipal;
import com.depth.deokive.system.security.util.QueryParamValidator;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증/인가 API")
public class AuthController {
    private final AuthService authService;
    private final EmailService emailService;

    // NO AUTH
    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "이메일 인증이 완료되지 않았습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 이메일입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public UserDto.UserResponse signUp(@RequestBody @Valid AuthDto.SignUpRequest request) {
        return authService.signUp(request);
    }

    // NO AUTH
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 아이디와 비밀번호로 로그인하여 JWT 토큰을 발급받습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "비밀번호가 일치하지 않습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "가입되지 않은 이메일입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AuthDto.LoginResponse login(@RequestBody @Valid AuthDto.LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    // NO AUTH
    @GetMapping("/email-exist")
    @Operation(summary = "이메일 중복 확인", description = "입력된 이메일의 사용 가능 여부를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "이메일 확인 성공")
    public AuthDto.ExistResponse checkEmail(@RequestParam("email") String email)
    {
        QueryParamValidator.validateEmail(email);
        return authService.checkEmailExist(email);
    }

    // NO AUTH
    @Hidden
    @GetMapping("/username-exist")
    @Operation(summary = "사용자 아이디 중복 확인", description = "입력된 사용자 아이디의 사용 가능 여부를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "사용자 아이디 확인 성공")
    public AuthDto.ExistResponse checkUsername(@RequestParam("username") String username) {
        QueryParamValidator.validateUsername(username);
        return authService.checkUsernameExist(username);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃 처리합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그아웃 성공", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "Logout Successful"))),
            @ApiResponse(responseCode = "401", description = "인증 토큰(JWT)이 누락되었습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("\n🔥 로그아웃 !\n");
        authService.logout(request, response);
        return ResponseEntity.ok("Logout Successful");
    }

    @DeleteMapping("/delete")
    @Operation(summary = "회원 탈퇴", description = "현재 로그인된 사용자의 계정을 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "회원 탈퇴 성공", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "Soft Delete User Successful"))),
            @ApiResponse(responseCode = "401", description = "인증 토큰이 누락되었습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<String> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.delete(userPrincipal, request, response);
        return ResponseEntity.ok("Soft Delete User Successful");
    }

    // NO AUTH
    @PostMapping("/refresh")
    @Operation(summary = "리프레시 토큰", description = "리프레시 토큰으로 새로운 액세스 토큰과 리프레시 토큰을 발급받습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "리프레시 토큰 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 리프레시 토큰입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<JwtDto.TokenExpiresInfo> refresh(
            @RequestParam(value = "rememberMe", defaultValue = "false") boolean rememberMe,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return ResponseEntity.ok(authService.refreshTokens(request, response, rememberMe));
    }

    // NO AUTH
    @Hidden
    @PostMapping("/is-blacklisted-rtk")
    public boolean isRtkBlacklisted(@RequestParam("refreshToken") String refreshToken) {
        return authService.isRtkBlacklisted(refreshToken);
    }

    // NO AUTH
    @Hidden
    @PostMapping("/is-blacklisted-atk")
    public boolean isAtkBlacklisted(@RequestParam("accessToken") String accessToken) {
        return authService.isAtkBlacklisted(accessToken);
    }

    @PostMapping("/is-token-active")
    @Operation(summary = "TokenPair 유효성 검증", description = "ATK와 RTK의 유효성을 검증합니다.")
    @ApiResponse(responseCode = "200", description = "TokenPair 유효성 검증")
    public boolean isTokenActive(HttpServletRequest request) {
        return authService.isTokenActive(request);
    }

    // NO AUTH
    @PostMapping("/reset-pw")
    @Operation(summary = "비밀번호 재설정", description = "비밀번호를 재설정 합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "비밀번호 재설정 성공"),
            @ApiResponse(responseCode = "401", description = "이메일 인증이 완료되지 않았습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "가입되지 않은 이메일입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<String> resetPassword(@RequestBody @Valid AuthDto.ResetPasswordRequest request)
    {
        authService.resetPassword(request);
        return ResponseEntity.ok("Successful Reset Password!");
    }

    // NO AUTH
    @PostMapping("/email/send")
    @Operation(summary = "이메일 전송", description = "이메일을 전송합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "이메일 전송 요청 접수됨"),
            @ApiResponse(responseCode = "500", description = "이메일 전송 실패 또는 Redis 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "메일 서버 또는 Redis 연결 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<String> sendEmail(@RequestParam String email) {
        emailService.sendEmail(email);
        return ResponseEntity.accepted().body("이메일 전송 요청이 접수되었습니다.");
    }

    // NO AUTH
    @PostMapping("/email/verify")
    @Operation(summary = "이메일 검증", description = "이메일을 검증합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "이메일 검증 성공"),
            @ApiResponse(responseCode = "400", description = "인증 코드가 일치하지 않거나 만료되었습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Redis 연결 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<String> verifyEmail(@RequestBody @Valid AuthDto.VerifyEmailRequest request) {
        emailService.verifyEmailCode(request.getEmail(), request.getCode(), request.getPurpose());
        return ResponseEntity.ok("이메일 인증이 완료되었습니다.");
    }

    @GetMapping("/social/me")
    @Operation(summary = "소셜 유저 및 토큰 만료기간 정보 조회", description = "소셜 로그인된 사용자의 정보 및 토큰 만료기간을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "소셜 유저 및 토큰 만료기간 정보 조회 성공"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AuthDto.LoginResponse socialRetrieve(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request
    ) {
        return authService.socialRetrieve(userPrincipal, request);
    }
}
