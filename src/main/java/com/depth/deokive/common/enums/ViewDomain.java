package com.depth.deokive.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ViewDomain {
    POST("post"),
    ARCHIVE("archive");

    private final String prefix; // redis key에 사용할 소문자 접두사
}