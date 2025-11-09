package com.depth.deokive.system.security.jwt.exception;

import com.depth.deokive.system.exception.model.ErrorCode;

public class JwtMissingException extends JwtRestException {
    public JwtMissingException() {
        super(ErrorCode.JWT_MISSING);
    }

    public JwtMissingException(String message) {
        super(ErrorCode.JWT_MISSING, message);
    }

    public JwtMissingException(Throwable cause) {
        super(ErrorCode.JWT_MISSING, cause);
    }
}
