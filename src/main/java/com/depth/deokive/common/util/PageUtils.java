package com.depth.deokive.common.util;

import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import org.springframework.data.domain.Page;

public final class PageUtils {
    public static void validatePageRange(Page<?> page){
        if (page.getTotalPages() > 0 && page.getNumber() >= page.getTotalPages()) {
            throw new RestException(ErrorCode.PAGE_NOT_FOUND);
        }
    }
}