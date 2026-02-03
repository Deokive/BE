package com.depth.deokive.system.config.redis;

import com.depth.deokive.domain.post.service.RepostSseSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
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

    @Bean
    public RedisTemplate<String, Long> longRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key는 문자열
        template.setKeySerializer(new StringRedisSerializer());

        // Value는 Long이지만, Redis에는 문자열("123")로 저장되도록 설정
        // 이 Serializer가 'Long <-> String' 변환을 자동으로 최적화해서 수행함
        template.setValueSerializer(new GenericToStringSerializer<>(Long.class));

        return template;
    }

    /**
     * Redis Pub/Sub 리스너 컨테이너
     * - SSE 이벤트 전파에 사용
     * - 스케일아웃 시 여러 인스턴스 간 이벤트 동기화
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RepostSseSubscriber repostSseSubscriber) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Repost 완료 이벤트 채널 구독
        container.addMessageListener(repostSseSubscriber,
                new ChannelTopic(RepostSseSubscriber.CHANNEL));

        log.info("[Redis Pub/Sub] Subscribed to channel: {}", RepostSseSubscriber.CHANNEL);
        return container;
    }
}
