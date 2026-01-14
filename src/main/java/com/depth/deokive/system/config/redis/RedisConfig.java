package com.depth.deokive.system.config.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        redisTemplate.setConnectionFactory(redisConnectionFactory());

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());

        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());

        redisTemplate.setDefaultSerializer(new StringRedisSerializer());

        return redisTemplate;
    }

    @Bean(name = "redisBlacklistTemplate")
    public RedisTemplate<String, Object> redisBlacklistTemplate() {
        RedisTemplate<String, Object> blacklistTemplate = new RedisTemplate<>();

        blacklistTemplate.setConnectionFactory(redisConnectionFactory());

        // Key serializer
        blacklistTemplate.setKeySerializer(new StringRedisSerializer());
        blacklistTemplate.setHashKeySerializer(new StringRedisSerializer());

        // Value serializer
        blacklistTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        blacklistTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        return blacklistTemplate;
    }

    @Bean(name = "emailTemplate")
    public StringRedisTemplate redisEmailTemplate() {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory());
        return template;
    }

    @Bean
    public DefaultRedisScript<Long> likeScript() {
        // Lua Script
        String script =
                "local added = redis.call('SADD', KEYS[1], ARGV[1]) " +
                        "if added == 1 then " +
                        "  redis.call('INCR', KEYS[2]) " +
                        "  redis.call('SREM', KEYS[1], ARGV[2]) " +
                        "  redis.call('EXPIRE', KEYS[1], ARGV[3]) " +
                        "  redis.call('EXPIRE', KEYS[2], ARGV[3]) " +
                        "  return 1 " +
                        "else " +
                        "  redis.call('SREM', KEYS[1], ARGV[1]) " +
                        "  redis.call('DECR', KEYS[2]) " +
                        "  return 0 " +
                        "end";
        return new DefaultRedisScript<>(script, Long.class);
    }
}
