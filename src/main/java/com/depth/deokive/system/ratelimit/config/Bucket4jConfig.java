package com.depth.deokive.system.ratelimit.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class Bucket4jConfig {

    @Value("${spring.data.redis.host}") private String redisHost;
    @Value("${spring.data.redis.port}") private int redisPort;
    @Value("${spring.data.redis.password:}") private String redisPassword;

    // RateLimit 전용 EventLoop 리소스 (다른 Redis 사용과 격리)
    @Bean(destroyMethod = "shutdown")
    public ClientResources bucket4jClientResources() {
        return DefaultClientResources.builder()
                .ioThreadPoolSize(2)          // RateLimit 전용 IO 스레드
                .computationThreadPoolSize(2) // 계산 스레드
                .build();
    }

    /**
     * FAIL_OPEN용 (짧은 timeout)
     */
    @Bean(name = "lettuceRedisClientFailOpen", destroyMethod = "shutdown")
    public RedisClient lettuceRedisClientFailOpen(RateLimitProperties props,
                                                  ClientResources bucket4jClientResources) {
        return createRedisClient("FAIL_OPEN",
                props.getRedis().getTimeoutFailOpen(),
                bucket4jClientResources);
    }

    /**
     * FAIL_CLOSED용 (조금 더 긴 timeout)
     */
    @Bean(name = "lettuceRedisClientFailClosed", destroyMethod = "shutdown")
    public RedisClient lettuceRedisClientFailClosed(RateLimitProperties props,
                                                    ClientResources bucket4jClientResources) {
        return createRedisClient("FAIL_CLOSED",
                props.getRedis().getTimeoutFailClosed(),
                bucket4jClientResources);
    }

    // ClientOptions로 Command Timeout 상한 강제
    private RedisClient createRedisClient(String label, Duration timeout, ClientResources resources) {

        RedisURI.Builder builder = RedisURI.Builder.redis(redisHost, redisPort)
                .withTimeout(timeout); // 연결 레벨 timeout

        if (redisPassword != null && !redisPassword.isBlank()) {
            builder.withPassword(redisPassword.toCharArray());
        }

        RedisURI redisUri = builder.build();

        RedisClient client = RedisClient.create(resources, redisUri);

        client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .timeoutOptions(TimeoutOptions.enabled(timeout))
                .build());

        log.info("Bucket4j Redis connection initialized({}): {}:{}, timeout={}",
                label, redisHost, redisPort, timeout);

        return client;
    }

    @Bean(name = "lettuceConnectionFailOpen", destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> lettuceConnectionFailOpen(
            RedisClient lettuceRedisClientFailOpen
    ) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return lettuceRedisClientFailOpen.connect(codec);
    }

    @Bean(name = "lettuceConnectionFailClosed", destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> lettuceConnectionFailClosed(
            RedisClient lettuceRedisClientFailClosed
    ) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return lettuceRedisClientFailClosed.connect(codec);
    }

    @Bean(name = "proxyManagerFailOpen")
    public LettuceBasedProxyManager<String> proxyManagerFailOpen(
            StatefulRedisConnection<String, byte[]> lettuceConnectionFailOpen,
            RateLimitProperties props
    ) {
        return buildProxyManager(lettuceConnectionFailOpen, props);
    }

    @Bean(name = "proxyManagerFailClosed")
    public LettuceBasedProxyManager<String> proxyManagerFailClosed(
            StatefulRedisConnection<String, byte[]> lettuceConnectionFailClosed,
            RateLimitProperties props
    ) {
        return buildProxyManager(lettuceConnectionFailClosed, props);
    }

    private LettuceBasedProxyManager<String> buildProxyManager(
            StatefulRedisConnection<String, byte[]> connection,
            RateLimitProperties props
    ) {
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(
                                        props.getExpiration().getRefillToMaxJitter()
                                )
                )
                .build();
    }
}
