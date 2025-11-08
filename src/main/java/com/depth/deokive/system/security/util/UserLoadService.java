package com.depth.deokive.system.security.util;

import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserLoadService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<UserPrincipal> loadUserById(Long userId) {
        return userRepository.findById(userId)
                .map(UserPrincipal::from);
    }

    @Transactional(readOnly = true)
    public Optional<UserPrincipal> loadUserByEmail(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        return user.map(UserPrincipal::from);
    }
}
