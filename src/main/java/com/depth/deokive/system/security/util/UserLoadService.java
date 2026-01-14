package com.depth.deokive.system.security.util;

import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoadService {
    private final UserRepository userRepository;

    // sync = true 설정 시, 여러 스레드가 동시에 접근해도 한 명만 DB에 가고 나머지는 첫 번째 스레드가 채워줄 때까지 대기함
    @Cacheable(value = "userPrincipal", key = "#userId", sync = true)
    @Transactional(readOnly = true)
    public Optional<UserPrincipal> loadUserById(Long userId) {
        log.info("🟢 UserLoadService loadUserById called with"); // 이 로그가 찍히면 실제 DB에 쿼리가 나가는 상황 (Cache Miss)
        return userRepository.findById(userId)
                .map(UserPrincipal::from);
    }

    @Cacheable(value = "userPrincipal", key = "#username", sync = true) // unless = "#result == null"
    @Transactional(readOnly = true)
    public Optional<UserPrincipal> loadUserByUsername(String username) {
        log.info("🟢 UserLoadService loadUserByUsername called with"); // 이 로그가 찍히면 실제 DB에 쿼리가 나가는 상황 (Cache Miss)
        Optional<User> user = userRepository.findByUsername(username);
        return user.map(UserPrincipal::from);
    }
}
