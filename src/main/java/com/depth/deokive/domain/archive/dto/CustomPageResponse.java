package com.depth.deokive.domain.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
public class CustomPageResponse<T> {
    private List<T> content;
    private PageInfo page;

    public static <T> CustomPageResponse<T> of(Page<T> pageData) {
        return new CustomPageResponse<>(
                pageData.getContent(),
                PageInfo.from(pageData)
        );
    }
}
