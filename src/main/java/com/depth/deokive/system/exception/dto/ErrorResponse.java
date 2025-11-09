package com.depth.deokive.system.exception.dto;

import com.depth.deokive.system.exception.model.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@Builder
public class ErrorResponse {
    public final HttpStatus status;
    public final String error;
    public final String message;

    private ErrorResponse(ErrorCode errorCode) {
        this.status = errorCode.getStatus();
        this.error = errorCode.name();
        this.message = errorCode.getMessage();
    }

    private ErrorResponse(ErrorCode errorCode, String message) {
        this.status = errorCode.getStatus();
        this.error = errorCode.name();
        this.message = message;
    }

    private ErrorResponse(HttpStatus status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode, message);
    }

    public static ErrorResponse of(HttpStatus httpStatus, String error, String message) {
        return new ErrorResponse(httpStatus, error, message);
    }
}
