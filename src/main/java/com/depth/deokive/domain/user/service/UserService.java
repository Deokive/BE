package com.depth.deokive.domain.user.service;

import com.depth.deokive.domain.user.dto.UserDto;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDto.UserResponse update(UserPrincipal userPrincipal, UserDto.UserUpdateRequest request) {
        /*
         * Flow ::
         * Controller 에서 UserPrincipal 을 받아옴 -> 이건 PersistenceContext 에서 관리 되는게 아님
         * JPA Dirty Checking 으로 update 처리 되게 하려면, PersistenceContext 로 load 해야 한다.
         * Client 로부터 받은 UserPrincipal 이 실제 User 와 매칭 되는지 체크함과 동시에 Context 에 load 하기 위해서 사용
         */
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        validateDuplication(request ,foundUser);
        request.encodePassword(passwordEncoder);
        foundUser.update(request);
        return UserDto.UserResponse.from(foundUser);
    }

    @Transactional(readOnly = true)
    public UserDto.UserResponse retrieve(UserPrincipal userPrincipal) {
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        return UserDto.UserResponse.from(foundUser);
    }

    private void validateDuplication(UserDto.UserUpdateRequest request, User foundUser) {
        if (request.getEmail() != null &&
                !foundUser.getEmail().equals(request.getEmail()) &&
                userRepository.existsByEmail(request.getEmail())) {
            throw new RestException(ErrorCode.USER_USERNAME_ALREADY_EXISTS);
        }
    }
}