package com.depth.deokive.system.security.jwt.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class TokenRedisRepository {
    private final StringRedisTemplate stringRedisTemplate;

    // Whitelist
    private String keyRtkAllow(String subject) { return "rt:allow:" + subject; }
    private String keyRtkMeta(String uuid) { return "rt:meta:" + uuid; }

    // Blacklist
    private String keyRtkBlack(String uuid) { return "rt:black:" + uuid; }
    private String keyAtkBlack(String jti) { return "at:black:" + jti; }

    public void allowRtk(String subject, String uuid, Duration ttl) {
        stringRedisTemplate.opsForValue().set(keyRtkAllow(subject), uuid, ttl);
        stringRedisTemplate.opsForValue().set(keyRtkMeta(uuid), subject, ttl);
    }

    public String getAllowedRtk(String subject) {
        return stringRedisTemplate.opsForValue().get(keyRtkAllow(subject));
    }

    public void setBlacklistRtk(String uuid, Duration ttl) {
        stringRedisTemplate.opsForValue().set(keyRtkBlack(uuid), "1", ttl);
    }

    public boolean isRtkBlacklisted(String uuid) {
        String v = stringRedisTemplate.opsForValue().get(keyRtkBlack(uuid));
        return v != null;
    }

    public void clearAllowedRtk(String subject) {
        stringRedisTemplate.delete(keyRtkAllow(subject));
    }

    public void setBlacklistAtkJti(String jti, Duration ttl) {
        stringRedisTemplate.opsForValue().set(keyAtkBlack(jti), "1", ttl);
    }

    public boolean isAtkBlacklisted(String jti) {
        String v = stringRedisTemplate.opsForValue().get(keyAtkBlack(jti));
        return v != null;
    }
}
