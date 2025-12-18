package com.depth.deokive.domain.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
@AllArgsConstructor
public class PageInfo {
    private int size;
    private int pageNumber;
    private long totalElements;
    private int totalPages;
    private String sort;  // "createdAt"
    private String order; // "desc"
    private boolean hasPrev;
    private boolean hasNext;
    private boolean empty;

    // Page 객체로부터 메타데이터 추출하는 생성자 메서드
    public static PageInfo from(Page<?> page) {
        String sortProperty = "createdAt"; // 기본값
        String sortOrder = "desc";         // 기본값

        if (page.getSort().isSorted()) {
            page.getSort().stream().findFirst().ifPresent(order -> {
                // lambda 내부 변수 할당 제약으로 인해 단순화함.
                // 필요시 외부 변수나 DTO 필드에 직접 매핑
            });
            // 실제 구현 시 Pageable에서 Sort 정보를 문자열로 파싱하는 로직이 필요하나,
            // 간단하게 첫 번째 정렬 기준을 가져온다고 가정
            String[] sortSplit = page.getSort().toString().split(": ");
            if(sortSplit.length > 1) {
                sortProperty = sortSplit[0];
                sortOrder = sortSplit[1].toLowerCase();
            }
        }

        return PageInfo.builder()
                .size(page.getSize())
                .pageNumber(page.getNumber())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .sort(sortProperty)
                .order(sortOrder)
                .hasPrev(page.hasPrevious())
                .hasNext(page.hasNext())
                .empty(page.isEmpty())
                .build();
    }
}