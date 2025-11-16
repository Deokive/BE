package com.depth.deokive.system.config.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "mailTaskExecutor")
    public Executor getAsyncExecutor() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        int nThreads = coreCount * 2; // I/O Bound : Core Count * (2 ~ 4)
        int queueSize = nThreads * 10;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(nThreads);       // 기본 스레드 수
        executor.setMaxPoolSize(nThreads*2);      // 최대 스레드 수
        executor.setQueueCapacity(queueSize);     // 대기 큐 크기
        executor.setThreadNamePrefix("Email-Async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);  // 종료 시 대기
        executor.setAwaitTerminationSeconds(60);  // 최대 60초 대기
        executor.setKeepAliveSeconds(60);  // 유휴 스레드 정리
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    // 비동기 메서드에서 발생한 예외를 처리하는 핸들러
    @Slf4j
    static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("❌ Async method '{}' failed with parameters: {}", method.getName(), params, ex);
            // 여기에 알림 로직, 재시도 로직, 모니터링 로직 등을 추가
        }
    }
}
