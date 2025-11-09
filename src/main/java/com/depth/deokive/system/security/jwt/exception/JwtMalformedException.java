package com.depth.deokive.system.security.jwt.exception;

import com.depth.deokive.system.exception.model.ErrorCode;

public class JwtMalformedException extends JwtRestException {
    public JwtMalformedException() {
        super(ErrorCode.JWT_MALFORMED);
    }

    public JwtMalformedException(String message) {
        super(ErrorCode.JWT_MALFORMED, message);
    }

    public JwtMalformedException(Throwable cause) {
        super(ErrorCode.JWT_MALFORMED, cause);
    }
}