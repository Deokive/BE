package com.depth.deokive.domain.file.scheduler;

import com.depth.deokive.system.scheduler.FileCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

import org.springframework.batch.core.JobExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileCleanupSchedulerTest {

    @InjectMocks
    private FileCleanupScheduler scheduler;

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job fileCleanupJob;

    @Test
    @DisplayName("스케줄러 동작: 현재 시간을 파라미터로 담아 JobLauncher를 실행해야 한다.")
    void runCleanupJob_Trigger_Success() throws Exception {
        // when
        scheduler.runCleanupJob();

        // then
        // ArgumentCaptor를 사용하여 JobLauncher에 전달된 실제 파라미터를 포획
        ArgumentCaptor<JobParameters> jobParamsCaptor = ArgumentCaptor.forClass(JobParameters.class);

        // verify: jobLauncher.run()이 호출되었는지 확인
        // eq(fileCleanupJob): 올바른 Job 객체가 전달되었는지
        // capture(): 전달된 JobParameters 객체를 가로챔
        verify(jobLauncher).run(eq(fileCleanupJob), jobParamsCaptor.capture());

        JobParameters capturedParams = jobParamsCaptor.getValue();

        // 검증: "time" 파라미터가 포함되어 있어야 함 (중복 실행 방지용 파라미터)
        // 값은 실행 시점의 시스템 시간이라 정확한 값 비교는 어렵지만, null이 아님을 확인
        assertThat(capturedParams.getLong("time")).isNotNull();
    }

    @Test
    @DisplayName("예외 처리: JobLauncher.run()이 JobExecutionException을 던질 때 예외를 catch하고 로그를 남긴다.")
    void runCleanupJob_ExceptionHandling() throws Exception {
        // given
        // JobExecutionException은 checked exception이므로 Mockito에서 직접 처리하기 어려움
        // 대신 RuntimeException으로 래핑하여 테스트 (실제 스케줄러는 catch (Exception e)로 모든 예외 처리)
        RuntimeException wrappedException = new RuntimeException(
                new JobExecutionException("Job execution failed")
        );
        
        // when: JobLauncher.run()이 예외를 던짐
        doThrow(wrappedException)
                .when(jobLauncher).run(eq(fileCleanupJob), any(JobParameters.class));

        // when & then: 예외가 catch되어 메서드가 정상적으로 종료되어야 함 (예외를 다시 던지지 않음)
        assertThatCode(() -> scheduler.runCleanupJob())
                .doesNotThrowAnyException();

        // then: JobLauncher.run()이 호출되었는지 확인
        ArgumentCaptor<JobParameters> jobParamsCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher, times(1)).run(eq(fileCleanupJob), jobParamsCaptor.capture());
        
        // JobParameters가 올바르게 전달되었는지 확인
        JobParameters capturedParams = jobParamsCaptor.getValue();
        assertThat(capturedParams.getLong("time")).isNotNull();
    }

    @Test
    @DisplayName("예외 처리: RuntimeException 발생 시에도 예외를 catch하고 로그를 남긴다.")
    void runCleanupJob_RuntimeExceptionHandling() throws Exception {
        // given
        RuntimeException runtimeException = new RuntimeException("Unexpected error occurred");
        
        // when: JobLauncher.run()이 RuntimeException을 던짐
        doThrow(runtimeException)
                .when(jobLauncher).run(eq(fileCleanupJob), any(JobParameters.class));

        // when & then: 예외가 catch되어 메서드가 정상적으로 종료되어야 함
        assertThatCode(() -> scheduler.runCleanupJob())
                .doesNotThrowAnyException();

        // then: JobLauncher.run()이 호출되었는지 확인
        verify(jobLauncher, times(1)).run(eq(fileCleanupJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("예외 처리: IllegalArgumentException 발생 시에도 예외를 catch하고 로그를 남긴다.")
    void runCleanupJob_IllegalArgumentExceptionHandling() throws Exception {
        // given
        IllegalArgumentException illegalArgumentException = new IllegalArgumentException("Invalid job parameters");

        // when: JobLauncher.run()이 IllegalArgumentException을 던짐
        doThrow(illegalArgumentException)
                .when(jobLauncher).run(eq(fileCleanupJob), any(JobParameters.class));

        // when & then: 예외가 catch되어 메서드가 정상적으로 종료되어야 함
        assertThatCode(() -> scheduler.runCleanupJob())
                .doesNotThrowAnyException();

        // then: JobLauncher.run()이 호출되었는지 확인
        verify(jobLauncher, times(1)).run(eq(fileCleanupJob), any(JobParameters.class));
    }
}