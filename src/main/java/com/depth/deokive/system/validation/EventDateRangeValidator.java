package com.depth.deokive.system.validation;

import com.depth.deokive.domain.event.dto.EventDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Event DTO의 날짜/시간 범위 검증 로직
 *
 * CreateRequest와 UpdateRequest 모두 처리
 * UpdateRequest는 필드가 Optional이므로 존재하는 필드만 검증
 */
public class EventDateRangeValidator implements ConstraintValidator<ValidEventDateRange, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null은 @NotNull에서 처리
        }

        if (value instanceof EventDto.CreateRequest) {
            return validateCreateRequest((EventDto.CreateRequest) value, context);
        } else if (value instanceof EventDto.UpdateRequest) {
            return validateUpdateRequest((EventDto.UpdateRequest) value, context);
        }

        return true;
    }

    /**
     * CreateRequest 검증
     * - startDate, endDate는 항상 존재 (@NotNull)
     */
    private boolean validateCreateRequest(EventDto.CreateRequest request, ConstraintValidatorContext context) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        Boolean hasTime = request.getHasTime();
        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();

        // Rule 1: startDate <= endDate
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            addConstraintViolation(context, "시작 날짜는 종료 날짜보다 이전이거나 같아야 합니다.");
            return false;
        }

        // Rule 2: hasTime=true일 때 startTime, endTime 필수
        if (Boolean.TRUE.equals(hasTime)) {
            if (startTime == null || endTime == null) {
                addConstraintViolation(context, "시간 설정이 활성화된 경우 시작 시간과 종료 시간을 모두 입력해야 합니다.");
                return false;
            }

            // Rule 3: 같은 날짜일 때 startTime <= endTime
            if (startDate != null && endDate != null && startDate.equals(endDate)) {
                if (startTime.isAfter(endTime)) {
                    addConstraintViolation(context, "시작 시간은 종료 시간보다 이전이거나 같아야 합니다.");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * UpdateRequest 검증
     * - 모든 필드가 Optional이므로 존재하는 필드만 검증
     */
    private boolean validateUpdateRequest(EventDto.UpdateRequest request, ConstraintValidatorContext context) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        Boolean hasTime = request.getHasTime();
        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();

        // Rule 1: startDate와 endDate가 둘 다 있으면 검증
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            addConstraintViolation(context, "시작 날짜는 종료 날짜보다 이전이거나 같아야 합니다.");
            return false;
        }

        // Rule 2: hasTime=true로 변경하는 경우, startTime/endTime 검증
        if (Boolean.TRUE.equals(hasTime)) {
            // startTime 또는 endTime 중 하나만 있는 경우 에러
            if ((startTime != null && endTime == null) || (startTime == null && endTime != null)) {
                addConstraintViolation(context, "시간 설정이 활성화된 경우 시작 시간과 종료 시간을 모두 입력해야 합니다.");
                return false;
            }
        }

        // Rule 3: 같은 날짜이고 시간이 모두 있을 때 startTime <= endTime
        if (startDate != null && endDate != null && startDate.equals(endDate)) {
            if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
                addConstraintViolation(context, "시작 시간은 종료 시간보다 이전이거나 같아야 합니다.");
                return false;
            }
        }

        return true;
    }

    /**
     * Custom error message 추가
     */
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}
