package com.depth.deokive.common.util;

import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import org.springframework.data.domain.Page;

public final class PageUtils {
    public static void validatePageRange(Page<?> page) {
        // Case 1: 데이터가 아예 없는 경우 (Total Pages == 0) -> 0페이지(첫 페이지) 요청은 허용하되, 0보다 큰 페이지 요청은 에러 처리
        if (page.getTotalPages() == 0) {
            if (page.getNumber() > 0) {
                throw new RestException(ErrorCode.PAGE_NOT_FOUND);
            }
            return; // 데이터가 없고 0페이지를 요청한 경우 -> 정상 (빈 리스트 반환)
        }

        // Case 2: 데이터가 있는 경우 (Total Pages > 0) -> 요청한 페이지 번호가 전체 페이지 수보다 크거나 같으면 에러 (0-indexed)
        if (page.getNumber() >= page.getTotalPages()) {
            throw new RestException(ErrorCode.PAGE_NOT_FOUND);
        }
    }
}