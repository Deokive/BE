package com.depth.deokive.system.config.rabbitmq;

import com.depth.deokive.common.enums.ViewDomain;
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

@Configuration
public class RabbitMQConfig {

    public static final String LIKE_QUEUE_NAME = "post.like.queue";
    public static final String LIKE_EXCHANGE_NAME = "post.like.exchange";
    public static final String LIKE_ROUTING_KEY = "post.like.key";

    // --- Post Domain ---
    @Bean public Queue postLikeQueue() { return new Queue(ViewDomain.POST.getQueueName(), true); }
    @Bean public DirectExchange postLikeExchange() { return new DirectExchange(ViewDomain.POST.getExchangeName()); }
    @Bean public Binding postLikeBinding() {
        return BindingBuilder.bind(postLikeQueue()).to(postLikeExchange()).with(ViewDomain.POST.getRoutingKey());
    }

    // --- Archive Domain ---
    @Bean public Queue archiveLikeQueue() { return new Queue(ViewDomain.ARCHIVE.getQueueName(), true); }
    @Bean public DirectExchange archiveLikeExchange() { return new DirectExchange(ViewDomain.ARCHIVE.getExchangeName()); }
    @Bean public Binding archiveLikeBinding() {
        return BindingBuilder.bind(archiveLikeQueue()).to(archiveLikeExchange()).with(ViewDomain.ARCHIVE.getRoutingKey());
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
        factory.setPrefetchCount(50);

        return factory;
    }
}