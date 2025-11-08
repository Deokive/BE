package com.depth.deokive.system.exception.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Global
    GLOBAL_ALREADY_RESOURCE(HttpStatus.CONFLICT, "GLOBAL ALREADY RESOURCE", "이미 존재하는 자원입니다."),
    GLOBAL_BAD_REQUEST(HttpStatus.BAD_REQUEST, "GLOBAL BAD REQUEST", "잘못된 요청입니다."),
    GLOBAL_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "GLOBAL METHOD NOT ALLOWED", "허용되지 않는 메서드입니다."),
    GLOBAL_INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "GLOBAL INVALID PARAMETER", "필수 요청 파라미터가 누락되었습니다."),
    GLOBAL_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL INTERNAL SERVER ERROR", "서버 내부에 오류가 발생했습니다."),

    // JWT Errors
    JWT_INVALID(HttpStatus.UNAUTHORIZED, "JWT INVALID", "유효하지 않은 토큰입니다."),
    JWT_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT EXPIRED", "만료된 토큰입니다."),
    JWT_NOT_FOUND(HttpStatus.UNAUTHORIZED, "JWT NOT FOUND", "인증 토큰을 찾을 수 없습니다."),
    JWT_MALFORMED(HttpStatus.UNAUTHORIZED, "JWT MALFORMED", "토큰 형식이 올바르지 않습니다."),
    JWT_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "JWT AUTHENTICATION FAILED", "토큰 인증에 실패했습니다."),
    JWT_CANNOT_GENERATE_TOKEN(HttpStatus.BAD_REQUEST, "JWT CANNOT GENERATE TOKEN", "토큰을 생성할 수 없습니다."),
    JWT_MISSING(HttpStatus.BAD_REQUEST, "JWT MISSING", "토큰이 누락되었습니다."),
    JWT_FAILED_PARSING(HttpStatus.UNAUTHORIZED, "JWT FAILED PARSING", "토큰을 파싱하는데 실패했습니다."),
    JWT_BLACKLIST(HttpStatus.UNAUTHORIZED, "JWT BLACKLIST", "블랙리스트에 해당하는 토큰입니다."),

    // AUTH
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH USER NOT FOUND", "등록된 유저를 찾을 수 없습니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH FORBIDDEN", "접근 권한이 없습니다."),
    AUTH_PASSWORD_NOT_MATCH(HttpStatus.UNAUTHORIZED, "AUTH PASSWORD NOT MATCH", "비밀번호가 올바르지 않습니다."),

    // OAUTH
    OAUTH_BAD_REQUEST(HttpStatus.BAD_REQUEST, "OAUTH BAD REQUEST", "OAUTH에 대해 잘못된 요청입니다."),
    OAUTH_USER_ALREADY_EXIST(HttpStatus.CONFLICT, "OAUTH_USER_ALREADY_EXIST", "이미 다른 소셜/일반 유저로 존재하는 사용자입니다."),

    // USER
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER NOT FOUND", "존재하지 않는 사용자입니다."),
    USER_USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER USERNAME ALREADY EXISTS", "중복되는 아이디입니다."),
    // USER_NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER NICKNAME ALREADY EXISTS", "중복되는 닉네임입니다."),
    USER_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER EMAIL ALREADY EXISTS", "중복되는 이메일입니다.");

    private final HttpStatus status;
    private final String error;
    private final String message;
}
