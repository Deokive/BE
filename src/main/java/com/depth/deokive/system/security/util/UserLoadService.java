package com.depth.deokive.system.security.util;

import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoadService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<UserPrincipal> loadUserById(Long userId) {
        log.info("🟢 UserLoadService loadUserById called with");
        return userRepository.findById(userId)
                .map(UserPrincipal::from);
    }

    @Transactional(readOnly = true)
    public Optional<UserPrincipal> loadUserByUsername(String username) {
        log.info("🟢 UserLoadService loadUserByUsername called with");
        Optional<User> user = userRepository.findByUsername(username);
        return user.map(UserPrincipal::from);
    }
}
