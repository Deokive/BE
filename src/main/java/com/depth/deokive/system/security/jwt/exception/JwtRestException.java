package com.depth.deokive.system.security.jwt.exception;

import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.Getter;

@Getter
public class JwtRestException extends RestException {
    public JwtRestException(ErrorCode errorCode) {
        super(errorCode);
    }

    public JwtRestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public JwtRestException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
