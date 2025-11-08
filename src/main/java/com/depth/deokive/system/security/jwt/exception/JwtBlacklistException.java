package com.depth.deokive.system.security.jwt.exception;

import com.depth.deokive.system.exception.model.ErrorCode;

public class JwtBlacklistException extends JwtRestException {
    public JwtBlacklistException() {
        super(ErrorCode.JWT_BLACKLIST);
    }

    public JwtBlacklistException(String message) {
        super(ErrorCode.JWT_BLACKLIST, message);
    }

    public JwtBlacklistException(Throwable cause) {
        super(ErrorCode.JWT_BLACKLIST, cause);
    }
}
