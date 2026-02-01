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
    JWT_MISSING(HttpStatus.UNAUTHORIZED, "JWT MISSING", "토큰이 누락되었습니다."),
    JWT_FAILED_PARSING(HttpStatus.UNAUTHORIZED, "JWT FAILED PARSING", "토큰을 파싱하는데 실패했습니다."),
    JWT_BLACKLIST(HttpStatus.UNAUTHORIZED, "JWT BLACKLIST", "블랙리스트에 해당하는 토큰입니다."),

    // AUTH
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH USER NOT FOUND", "등록된 유저를 찾을 수 없습니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH FORBIDDEN", "접근 권한이 없습니다."),
    AUTH_PASSWORD_NOT_MATCH(HttpStatus.UNAUTHORIZED, "AUTH PASSWORD NOT MATCH", "비밀번호가 올바르지 않습니다."),
    AUTH_EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "AUTH_EMAIL_NOT_VERIFIED", "이메일 인증이 완료되지 않았습니다."),
    AUTH_EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_CODE_INVALID", "이메일 인증코드가 올바르지 않거나, 만료되었습니다."),
    AUTH_EMAIL_CODE_NOT_MATCHED(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_CODE_NOT_MATCHED", "이메일 인증코드가 일치하지 않습니다."),

    // OAUTH
    OAUTH_BAD_REQUEST(HttpStatus.BAD_REQUEST, "OAUTH BAD REQUEST", "OAUTH에 대해 잘못된 요청입니다."),
    OAUTH_USER_ALREADY_EXIST(HttpStatus.CONFLICT, "OAUTH_USER_ALREADY_EXIST", "이미 다른 소셜/일반 유저로 존재하는 사용자입니다."),

    // USER
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER NOT FOUND", "존재하지 않는 사용자입니다."),
    USER_USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER USERNAME ALREADY EXISTS", "중복되는 아이디입니다."),
    USER_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER EMAIL ALREADY EXISTS", "중복되는 이메일입니다."),
    USER_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "USER UNAUTHORIZED", "인증되지 않은 유저입니다."),

    // REDIS Errors
    REDIS_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_CONNECTION_FAILED", "Redis 서버에 연결할 수 없습니다."),
    REDIS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_ERROR", "Redis 처리 중 오류가 발생했습니다."),
    REDIS_COMMAND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_COMMAND_FAILED", "Redis 명령 실행 중 오류가 발생했습니다."),
    REDIS_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "REDIS_TIMEOUT", "Redis 서버 응답 시간이 초과되었습니다."),

    // MAIL Errors
    MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MAIL SEND FAILED", "이메일 전송에 실패했습니다."),
    MAIL_INVALID_ADDRESS(HttpStatus.BAD_REQUEST, "MAIL INVALID ADDRESS", "유효하지 않은 이메일 주소입니다."),
    MAIL_TEMPLATE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "MAIL TEMPLATE ERROR", "이메일 템플릿 처리 중 오류가 발생했습니다."),
    MAIL_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "MAIL CONNECTION FAILED", "메일 서버에 연결할 수 없습니다."),

    // FILE Errors
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE NOT FOUND", "파일을 찾을 수 없습니다."),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "FILE STORAGE ERROR", "파일 저장 중 오류가 발생했습니다."),
    FILE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "FILE INVALID FORMAT", "유효하지 않은 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "FILE SIZE EXCEEDED", "파일 크기가 허용된 한도를 초과했습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FILE ACCESS DENIED", "파일에 접근할 수 있는 권한이 없습니다."),

    // POST Errors
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST NOT FOUND", "존재하지 않는 게시글입니다."),
    POST_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST CREATION FAILED", "게시글 생성에 실패했습니다."),
    POST_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST UPDATE FAILED", "게시글 수정에 실패했습니다."),
    POST_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST DELETE FAILED", "게시글 삭제에 실패했습니다."),

    // REPOST Errors
    REPOST_NOT_FOUND(HttpStatus.NOT_FOUND, "REPOST NOT FOUND", "존재하지 않는 리포스트입니다."),
    REPOST_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REPOST CREATION FAILED", "리포스트 생성에 실패했습니다."),
    REPOST_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REPOST UPDATE FAILED", "리포스트 수정에 실패했습니다."),
    REPOST_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REPOST DELETE FAILED", "리포스트 삭제에 실패했습니다."),

    REPOST_TAB_NOT_FOUND(HttpStatus.NOT_FOUND, "REPOST TAB NOT FOUND", "존재하지 않는 리포스트 탭입니다."),
    REPOST_TAB_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REPOST TAB CREATION FAILED", "리포스트 탭 생성에 실패했습니다."),
    REPOST_TAB_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REPOST TAB UPDATE FAILED", "리포스트 탭 수정에 실패했습니다."),
    REPOST_TAB_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REPOST TAB DELETE FAILED", "리포스트 탭 삭제에 실패했습니다."),
    REPOST_TAB_LIMIT_EXCEED(HttpStatus.INTERNAL_SERVER_ERROR, "REPOST TAB LIMIT EXCEED", "가능한 리포스트 탭 갯수를 초과했습니다."),
    REPOST_TAB_AND_POST_DUPLICATED(HttpStatus.CONFLICT, "REPOST_TAB_AND_POST_DUPLICATED", "중복된 리포스트입니다."),

    // URL-specific Repost errors
    REPOST_INVALID_URL(HttpStatus.BAD_REQUEST, "REPOST_INVALID_URL", "유효하지 않은 URL입니다."),
    REPOST_URL_UNREACHABLE(HttpStatus.SERVICE_UNAVAILABLE, "REPOST_URL_UNREACHABLE", "URL에 접근할 수 없습니다."),
    REPOST_URL_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "REPOST_URL_TIMEOUT", "URL 요청 시간이 초과되었습니다."),
    REPOST_URL_DUPLICATED(HttpStatus.CONFLICT, "REPOST_URL_DUPLICATED", "이미 해당 탭에 저장된 URL입니다."),

    // PAGE Errors
    PAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "PAGE NOT FOUND", "존재하지 않는 페이지입니다."),

    // ARCHIVE Errors
    ARCHIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARCHIVE_NOT_FOUND", "존재하지 않는 아카이브입니다."),
    ARCHIVE_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ARCHIVE_CREATION_FAILED", "아카이브 생성에 실패했습니다."),
    ARCHIVE_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ARCHIVE_UPDATE_FAILED", "아카이브 수정에 실패했습니다."),
    ARCHIVE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ARCHIVE_DELETE_FAILED", "아카이브 삭제에 실패했습니다."),

    // DIARY Errors
    DIARY_NOT_FOUND(HttpStatus.NOT_FOUND, "DIARY_NOT_FOUND", "존재하지 않는 다이어리입니다."),
    DIARY_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DIARY_CREATION_FAILED", "다이어리 생성에 실패했습니다."),
    DIARY_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DIARY_UPDATE_FAILED", "다이어리 수정에 실패했습니다."),
    DIARY_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DIARY_DELETE_FAILED", "다이어리 삭제에 실패했습니다."),

    // TICKET Errors
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "존재하지 않는 티켓입니다."),
    TICKET_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TICKET_CREATION_FAILED", "티켓 생성에 실패했습니다."),
    TICKET_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TICKET_UPDATE_FAILED", "티켓 수정에 실패했습니다."),
    TICKET_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TICKET_DELETE_FAILED", "티켓 삭제에 실패했습니다."),

    // EVENT Errors
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "존재하지 않는 이벤트입니다."),
    EVENT_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EVENT_CREATION_FAILED", "이벤트 생성에 실패했습니다."),
    EVENT_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EVENT_UPDATE_FAILED", "이벤트 수정에 실패했습니다."),
    EVENT_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EVENT_DELETE_FAILED", "이벤트 삭제에 실패했습니다."),
    EVENT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "EVENT_LIMIT_EXCEEDED", "이벤트는 일정 당 최대 4개까지만 등록할 수 있습니다."),

    // STICKER Errors
    STICKER_ALREADY_EXISTS(HttpStatus.CONFLICT, "STICKER_ALREADY_EXISTS", "이미 해당 일정에 스티커가 존재합니다."),
    STICKER_NOT_FOUND(HttpStatus.NOT_FOUND, "STICKER_NOT_FOUND", "존재하지 않는 스티커입니다."),

    // DB Errors
    DB_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "DB CONNECTION FAILED", "데이터베이스 연결에 실패했습니다."),
    DB_QUERY_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "DB QUERY TIMEOUT", "쿼리 실행 시간이 초과되었습니다."),
    DB_DEADLOCK(HttpStatus.CONFLICT, "DB DEADLOCK", "데드락이 발생했습니다."),
    DB_OPTIMISTIC_LOCK_FAILED(HttpStatus.CONFLICT, "DB OPTIMISTIC LOCK FAILED", "낙관적 락 실패"),
    DB_PESSIMISTIC_LOCK_FAILED(HttpStatus.CONFLICT, "DB PESSIMISTIC LOCK FAILED", "비관적 락 실패"),
    DB_INCORRECT_RESULT_SIZE(HttpStatus.INTERNAL_SERVER_ERROR, "DB INCORRECT RESULT SIZE", "결과 크기 불일치"),
    DB_TRANSACTION_SERIALIZATION_FAILED(HttpStatus.CONFLICT, "DB TRANSACTION SERIALIZATION FAILED", "트랜잭션 직렬화 실패"),
    DB_DATA_ACCESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "DB DATA ACCESS ERROR", "데이터 접근 오류"),
    DB_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "DB DATA NOT FOUND", "존재하지 않는 데이터입니다."),
    DB_DATA_TOO_LONG(HttpStatus.BAD_REQUEST, "DB DATA TOO LONG", "데이터 길이가 허용된 한도를 초과했습니다."),
    DB_NOT_NULL_VIOLATION(HttpStatus.BAD_REQUEST, "DB NOT NULL VIOLATION", "필수 필드가 누락되었습니다."),
    DB_FOREIGN_KEY_VIOLATION(HttpStatus.BAD_REQUEST, "DB FOREIGN KEY VIOLATION", "참조 무결성 제약 조건을 위반했습니다."),

    // FRIEND Errors
    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIEND_NOT_FOUND", "존재하지 않는 친구입니다."),
    FRIEND_SELF_BAD_REQUEST(HttpStatus.BAD_REQUEST, "FRIEND_SELF_BAD_REQUEST", "자기 자신에게 친구 요청을 보낼 수 없습니다."),
    FRIEND_REQUEST_CONFLICT(HttpStatus.CONFLICT, "FRIEND_REQUEST_CONFLICT", "상대방이 이미 친구 요청을 한 상태입니다."), // 409에러
    FRIEND_ALREADY_REQUESTED(HttpStatus.BAD_REQUEST, "FRIEND_ALREADY_REQUESTED", "이미 친구 요청을 보냈습니다."),
    FRIEND_ALREADY_EXISTS(HttpStatus.CONFLICT, "FRIEND_ALREADY_EXISTS", "이미 친구 관계입니다."), // 409 에러
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND,"FRIEND_REQUEST_NOT_FOUND", "받은 친구 요청이 존재하지 않습니다."),
    FRIEND_SEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND,"FRIEND_REQUEST_NOT_FOUND", "보낸 친구 요청이 존재하지 않습니다."),
    FRIEND_REQUEST_NOT_PENDING(HttpStatus.BAD_REQUEST, "FRIEND_REQUEST_NOT_PENDING", "대기 상태의 친구 요청이 아닙니다."),
    FRIEND_RECOVER_BAD_REQUEST(HttpStatus.BAD_REQUEST, "FRIEND_CANNOT_RECOVER", "친구 관계를 복구할 수 없는 상태입니다."),
    FRIEND_CANCELED_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIEND_CANCELED_NOT_FOUND", "끊은 친구가 존재하지 않습니다."),
    FRIEND_INVALID_TYPE(HttpStatus.BAD_REQUEST, "FRIEND_INVALID_TYPE", "유효하지 않은 친구 요청 타입입니다."),

    // COMMENT Errors
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", " 존재하지 않는 댓글입니다.");

    private final HttpStatus status;
    private final String error;
    private final String message;
}