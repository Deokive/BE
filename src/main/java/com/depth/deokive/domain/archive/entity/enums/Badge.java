package com.depth.deokive.domain.archive.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Badge {
    // 순서 중요: 낮은 등급 -> 높은 등급
    NEWBIE("뉴비", 0),             // 가입 즉시
    FANS("팬", 7),                 // 1주 (7일)
    SUPPORTER("서포터", 30),        // 1달 (30일)
    STAN("스탠", 120),             // 4달 (120일)
    MASTER("마스터", 365);         // 1년 (365일)

    private final String description;
    private final int requiredDays; // createdAt 기준 필요한 누적 일수

    // 다음 등급 반환 (업데이트 대상 식별용)
    public Badge getNext() {
        return this.ordinal() < values().length - 1 ? values()[this.ordinal() + 1] : null;
    }
}