// package com.depth.deokive.domain.file.batch;
//
// import com.depth.deokive.domain.file.entity.File;
// import com.depth.deokive.domain.file.repository.FileRepository;
// import com.depth.deokive.system.config.file.FileCleanupBatchConfig;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.batch.item.ItemProcessor;
// import org.springframework.test.util.ReflectionTestUtils;
// import software.amazon.awssdk.services.s3.S3Client;
// import software.amazon.awssdk.services.s3.model.DeleteObjectResponse; // 추가됨
// import software.amazon.awssdk.services.s3.model.S3Exception;
//
// import javax.sql.DataSource;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.atomic.AtomicReference;
// import java.util.function.Consumer;
//
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.*;
//
// @ExtendWith(MockitoExtension.class)
// class FileCleanupProcessorTest {
//
//     @Mock
//     private S3Client s3Client;
//
//     @Mock
//     private FileRepository fileRepository;
//
//     @Mock
//     private DataSource dataSource;
//
//     private ItemProcessor<File, File> processor;
//
//     @BeforeEach
//     void setUp() {
//         FileCleanupBatchConfig config = new FileCleanupBatchConfig(
//                 null,
//                 null,
//                 s3Client,
//                 fileRepository,
//                 dataSource
//         );
//
//         ReflectionTestUtils.setField(config, "bucketName", "test-bucket");
//         ReflectionTestUtils.setField(config, "maxRetryAttempts", 3);
//         ReflectionTestUtils.setField(config, "retryDelayMs", 1L);
//
//         processor = config.s3DeleteProcessor();
//     }
//
//     @Test
//     @DisplayName("정상: S3 삭제 성공 시 파일 객체를 그대로 반환한다.")
//     void processor_Success() throws Exception {
//         // given
//         File file = File.builder().id(1L).s3ObjectKey("files/success.jpg").build();
//
//         // [수정] void가 아니므로 dummy response 반환 설정
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenReturn(DeleteObjectResponse.builder().build());
//
//         // when
//         File result = processor.process(file);
//
//         // then
//         assertThat(result).isEqualTo(file);
//         verify(s3Client, times(1)).deleteObject(any(Consumer.class));
//     }
//
//     @Test
//     @DisplayName("재시도 성공: 1차 시도 실패 후 2차 시도에서 성공하면 정상 처리된다.")
//     void processor_Retry_Success() throws Exception {
//         // given
//         File file = File.builder().id(1L).s3ObjectKey("files/retry_success.jpg").build();
//
//         // [수정] 1회 예외 -> 2회 성공(Response 반환)
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenThrow(S3Exception.builder().message("Network Flakiness").build())
//                 .thenReturn(DeleteObjectResponse.builder().build());
//
//         // when
//         File result = processor.process(file);
//
//         // then
//         assertThat(result).isEqualTo(file);
//         verify(s3Client, times(2)).deleteObject(any(Consumer.class));
//     }
//
//     @Test
//     @DisplayName("재시도 실패: 최대 횟수(3회)를 초과하면 최종적으로 예외를 던진다.")
//     void processor_Retry_Fail() {
//         // given
//         File file = File.builder().id(1L).s3ObjectKey("files/retry_fail.jpg").build();
//
//         // [수정] 계속 예외 발생
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenThrow(S3Exception.builder().message("Persistent Error").build());
//
//         // when & then
//         assertThatThrownBy(() -> processor.process(file))
//                 .isInstanceOf(S3Exception.class)
//                 .hasMessageContaining("Persistent Error");
//
//         // 3회 시도 확인
//         verify(s3Client, times(3)).deleteObject(any(Consumer.class));
//     }
//
//     @Test
//     @DisplayName("재시도 경계값: 1회 시도에서 성공하면 재시도 없이 처리된다.")
//     void processor_Retry_FirstAttempt_Success() throws Exception {
//         // given
//         File file = File.builder().id(1L).s3ObjectKey("files/first_attempt.jpg").build();
//
//         // when: 첫 시도에서 성공
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenReturn(DeleteObjectResponse.builder().build());
//
//         // when
//         File result = processor.process(file);
//
//         // then
//         assertThat(result).isEqualTo(file);
//         // 1회만 시도되어야 함
//         verify(s3Client, times(1)).deleteObject(any(Consumer.class));
//     }
//
//     @Test
//     @DisplayName("재시도 경계값: 2회 시도 후 성공하면 정상 처리된다.")
//     void processor_Retry_SecondAttempt_Success() throws Exception {
//         // given
//         File file = File.builder().id(1L).s3ObjectKey("files/second_attempt.jpg").build();
//
//         // when: 1회 실패 -> 2회 성공
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenThrow(S3Exception.builder().message("First attempt failed").build())
//                 .thenReturn(DeleteObjectResponse.builder().build());
//
//         // when
//         File result = processor.process(file);
//
//         // then
//         assertThat(result).isEqualTo(file);
//         // 2회 시도되어야 함
//         verify(s3Client, times(2)).deleteObject(any(Consumer.class));
//     }
//
//     @Test
//     @DisplayName("재시도 경계값: 3회 시도(최대 횟수) 후 성공하면 정상 처리된다.")
//     void processor_Retry_ThirdAttempt_Success() throws Exception {
//         // given
//         File file = File.builder().id(1L).s3ObjectKey("files/third_attempt.jpg").build();
//
//         // when: 1회, 2회 실패 -> 3회 성공 (최대 횟수에서 성공)
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenThrow(S3Exception.builder().message("First attempt failed").build())
//                 .thenThrow(S3Exception.builder().message("Second attempt failed").build())
//                 .thenReturn(DeleteObjectResponse.builder().build());
//
//         // when
//         File result = processor.process(file);
//
//         // then
//         assertThat(result).isEqualTo(file);
//         // 3회 시도되어야 함 (최대 횟수)
//         verify(s3Client, times(3)).deleteObject(any(Consumer.class));
//     }
//
//     @Test
//     @DisplayName("다양한 예외 타입: RuntimeException 발생 시에도 재시도 메커니즘이 동작한다.")
//     void processor_Retry_WithRuntimeException() throws Exception {
//         // given
//         File file = File.builder().id(1L).s3ObjectKey("files/runtime_exception.jpg").build();
//
//         // when: RuntimeException 발생 후 성공
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenThrow(new RuntimeException("Unexpected runtime error"))
//                 .thenReturn(DeleteObjectResponse.builder().build());
//
//         // when
//         File result = processor.process(file);
//
//         // then
//         assertThat(result).isEqualTo(file);
//         // 2회 시도되어야 함
//         verify(s3Client, times(2)).deleteObject(any(Consumer.class));
//     }
//
//     @Test
//     @DisplayName("다양한 예외 타입: IllegalArgumentException 발생 시에도 재시도 메커니즘이 동작한다.")
//     void processor_Retry_WithIllegalArgumentException() throws Exception {
//         // given
//         File file = File.builder().id(1L).s3ObjectKey("files/illegal_argument.jpg").build();
//
//         // when: IllegalArgumentException 발생 후 성공
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenThrow(new IllegalArgumentException("Invalid argument"))
//                 .thenReturn(DeleteObjectResponse.builder().build());
//
//         // when
//         File result = processor.process(file);
//
//         // then
//         assertThat(result).isEqualTo(file);
//         // 2회 시도되어야 함
//         verify(s3Client, times(2)).deleteObject(any(Consumer.class));
//     }
//
//     @Test
//     @DisplayName("다양한 예외 타입: RuntimeException이 최대 재시도 횟수를 초과하면 예외를 던진다.")
//     void processor_Retry_WithRuntimeException_Fail() {
//         // given
//         File file = File.builder().id(1L).s3ObjectKey("files/runtime_exception_fail.jpg").build();
//
//         // when: 계속 RuntimeException 발생
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenThrow(new RuntimeException("Persistent runtime error"));
//
//         // when & then
//         assertThatThrownBy(() -> processor.process(file))
//                 .isInstanceOf(RuntimeException.class)
//                 .hasMessageContaining("Persistent runtime error");
//
//         // 3회 시도 확인
//         verify(s3Client, times(3)).deleteObject(any(Consumer.class));
//     }
//
//     @Test
//     @DisplayName("인터럽트 처리: Thread.sleep() 중 인터럽트 발생 시 RuntimeException을 던진다.")
//     void processor_InterruptedException() throws Exception {
//         // given: 재시도 지연 시간을 충분히 크게 설정하여 인터럽트 가능한 시간 확보
//         FileCleanupBatchConfig config = new FileCleanupBatchConfig(
//                 null,
//                 null,
//                 s3Client,
//                 fileRepository,
//                 dataSource
//         );
//         ReflectionTestUtils.setField(config, "bucketName", "test-bucket");
//         ReflectionTestUtils.setField(config, "maxRetryAttempts", 3);
//         ReflectionTestUtils.setField(config, "retryDelayMs", 200L); // 충분한 지연 시간
//         ItemProcessor<File, File> processorWithDelay = config.s3DeleteProcessor();
//
//         File file = File.builder().id(1L).s3ObjectKey("files/interrupted.jpg").build();
//
//         AtomicReference<Exception> caughtException = new AtomicReference<>();
//         CountDownLatch processorStarted = new CountDownLatch(1);
//
//         when(s3Client.deleteObject(any(Consumer.class)))
//                 .thenAnswer(invocation -> {
//                     // 첫 시도 실패
//                     throw S3Exception.builder().message("Network error").build();
//                 });
//
//         // when: 별도 스레드에서 Processor 실행
//         Thread processorThread = new Thread(() -> {
//             processorStarted.countDown(); // Processor 시작 신호
//             try {
//                 processorWithDelay.process(file);
//             } catch (Exception e) {
//                 caughtException.set(e);
//             }
//         });
//
//         processorThread.start();
//         processorStarted.await(); // Processor가 시작될 때까지 대기
//
//         // 첫 시도가 실패하고 Thread.sleep()에 진입한 후 인터럽트
//         Thread.sleep(50); // Processor가 첫 시도 실패 후 sleep에 진입할 시간 확보
//         processorThread.interrupt();
//
//         processorThread.join(2000); // 최대 2초 대기
//
//         // then: InterruptedException이 발생하고 RuntimeException으로 래핑되어야 함
//         assertThat(caughtException.get()).isNotNull();
//         assertThat(caughtException.get())
//                 .isInstanceOf(RuntimeException.class)
//                 .hasMessageContaining("Retry delay interrupted");
//         assertThat(caughtException.get().getCause())
//                 .isInstanceOf(InterruptedException.class);
//     }
// }