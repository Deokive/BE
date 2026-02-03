package com.depth.deokive.system.config.rabbitmq;

import com.depth.deokive.common.enums.ViewLikeDomain;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;

/**
 * RabbitMQ Configuration
 *
 * [Virtual Threads 최적화]
 * - Java 21 Virtual Threads로 네트워크 I/O 병렬 처리 극대화
 * - OG 추출 같은 blocking I/O 작업에 최적
 * - 수천 개의 동시 작업도 소수의 Platform Thread로 처리 가능
 */
@Configuration
public class RabbitMQConfig {

    // --- Post Domain ---
    @Bean public Queue postLikeQueue() { return new Queue(ViewLikeDomain.POST.getQueueName(), true); }
    @Bean public DirectExchange postLikeExchange() { return new DirectExchange(ViewLikeDomain.POST.getExchangeName()); }
    @Bean public Binding postLikeBinding() {
        return BindingBuilder.bind(postLikeQueue()).to(postLikeExchange()).with(ViewLikeDomain.POST.getRoutingKey());
    }

    // --- Archive Domain ---
    @Bean public Queue archiveLikeQueue() { return new Queue(ViewLikeDomain.ARCHIVE.getQueueName(), true); }
    @Bean public DirectExchange archiveLikeExchange() { return new DirectExchange(ViewLikeDomain.ARCHIVE.getExchangeName()); }
    @Bean public Binding archiveLikeBinding() {
        return BindingBuilder.bind(archiveLikeQueue()).to(archiveLikeExchange()).with(ViewLikeDomain.ARCHIVE.getRoutingKey());
    }

    // --- Repost OG Extraction (비동기 처리) ---
    public static final String REPOST_OG_QUEUE = "repost.og.extraction";
    public static final String REPOST_OG_EXCHANGE = "repost.og.exchange";
    public static final String REPOST_OG_ROUTING_KEY = "repost.og.extract";

    @Bean public Queue repostOgQueue() { return new Queue(REPOST_OG_QUEUE, true); }
    @Bean public DirectExchange repostOgExchange() { return new DirectExchange(REPOST_OG_EXCHANGE); }
    @Bean public Binding repostOgBinding() {
        return BindingBuilder.bind(repostOgQueue()).to(repostOgExchange()).with(REPOST_OG_ROUTING_KEY);
    }

    // 4. JSON Converter
    @Bean public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Prefetch Count 설정 (OOM 방지)
    @Bean
    public SimpleRabbitListenerContainerFactory prefetchContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());

        // Consumer가 한 번에 가져갈 메시지 개수 제한
        // 이 설정이 없으면 큐에 쌓인 수만 개의 메시지를 한꺼번에 힙 메모리로 로딩하다가 서버가 죽음
        factory.setPrefetchCount(120);  // 120으로 증가 (동시 처리량에 맞춤)

        // Virtual Threads 사용 (Java 21)
        // - OG 추출 같은 blocking I/O 작업에 최적화
        // - 60개 동시 처리해도 실제 Platform Thread는 소수만 사용
        // - 네트워크 대기 중에는 다른 Virtual Thread가 실행됨
        factory.setTaskExecutor(new VirtualThreadTaskExecutor("og-extractor-"));

        return factory;
    }
}