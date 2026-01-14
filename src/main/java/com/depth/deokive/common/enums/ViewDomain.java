package com.depth.deokive.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ViewDomain {
    POST("post", "post.like.queue", "post.like.exchange", "post.like.key"),
    ARCHIVE("archive", "archive.like.queue", "archive.like.exchange", "archive.like.key");

    private final String prefix;
    private final String queueName;
    private final String exchangeName;
    private final String routingKey;
}