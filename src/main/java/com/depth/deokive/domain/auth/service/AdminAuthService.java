package com.depth.deokive.domain.auth.service;

import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private final UserRepository userRepository;

    @Transactional
    public void delete(UserPrincipal userPrincipal, HttpServletRequest request, HttpServletResponse response) {
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        validateAdminUser(foundUser);

        // TODO: 추후 도메인의 연관관계들이 엮이면, 점검 필요

        userRepository.delete(foundUser);
    }

    private void validateAdminUser(User user) {
        if(user.getRole() != Role.ADMIN){
            throw new RestException(ErrorCode.AUTH_FORBIDDEN, "Admin 사용자만 접근 가능합니다.");
        }
    }
}
