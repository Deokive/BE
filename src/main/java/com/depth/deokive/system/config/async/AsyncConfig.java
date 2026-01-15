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
        int coreCount = 2; // 실제 서버 환경의 CPU Core
        // int coreCount = Runtime.getRuntime().availableProcessors(); // 운영단에선 유연하게 처리하도록 이렇게 함
        int nThreads = coreCount * 3; // I/O Bound : Core Count * (2 ~ 4)
        int queueSize = 150; // Best 8 || 10 (지금은 10이 더 빠른 듯. 12는 느림)

        // TODO: 실제 운영 서버에서, 서비스 이전 MailHog로 테스트하면서 1차 튜닝하고, Gmail로 전환하여 2차 튜닝한다.
        // 그 이유는 MailHog는 전송 제한이 없는데, Gmail은 전송제한이 있음. 그리고 유의미한 메일이어야 함

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

    @Bean(name = "messagingTaskExecutor")
    public Executor messagingTaskExecutor() {
        int coreCount = Runtime.getRuntime().availableProcessors(); // t3.small = 2

        // I/O Blocking 계수 (I/O 작업 비중이 높을수록 값을 높임)
        // RabbitMQ 전송은 매우 빠른 I/O이므로 1.5 ~ 2배 정도면 적당함
        double ioBlockingCoefficient = 1.5;

        // 최대 트래픽 목표 (Spike Target)
        int targetConcurrentUsers = 1000;

        // 큐 버퍼 배수 (스파이크 대비 여유분)
        // 메모리가 허용하는 한 넉넉하게 잡아야 'CallerRuns'로 인한 메인 스레드 병목을 막음
        int queueBufferMultiplier = 2;

        // Formula: N_threads = N_cpu * (1 + Wait/Compute)
        int corePoolSize = (int) (coreCount * (1 + ioBlockingCoefficient));

        // 2. MaxPoolSize: 큐가 꽉 찼을 때 확장할 최대 스레드 수
        int maxPoolSize = corePoolSize * 2;

        // 3. QueueCapacity: 스레드가 바쁠 때 대기할 요청 수
        int queueCapacity = targetConcurrentUsers * queueBufferMultiplier; // 계산: 목표 동시 접속자 수 * 여유 배수

        // Executor 생성
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);

        executor.setThreadNamePrefix("MQ-Async-");

        // 큐 + MaxPool 초과 시 정책
        // CallerRunsPolicy: 큐가 터지면 요청한 놈(Tomcat 스레드)이 직접 발송함. -> 이렇게 되면 응답 속도가 느려지지만, 메시지 유실은 막음.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10); // MQ 전송은 빠르므로 짧게 대기
        executor.setKeepAliveSeconds(30);        // 유휴 스레드 정리 (메모리 절약)

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
