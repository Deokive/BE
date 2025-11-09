package com.depth.deokive.system.security.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class OriginUtils {

    // Helper Method
    public static List<String> originListParser(String allowedOrigins) {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
    }
}

