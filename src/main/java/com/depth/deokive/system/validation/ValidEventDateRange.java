package com.depth.deokive.system.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Event의 날짜/시간 범위 및 hasTime 정합성을 검증하는 Custom Validation Annotation
 *
 * 검증 규칙:
 * 1. startDate <= endDate (필수)
 * 2. hasTime=true일 때: startTime, endTime 모두 not null
 * 3. startDate == endDate && hasTime=true일 때: startTime <= endTime
 *
 * @see EventDateRangeValidator
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventDateRangeValidator.class)
@Documented
public @interface ValidEventDateRange {

    String message() default "이벤트 날짜/시간 범위가 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
