package com.depth.deokive.domain.comment.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class CommentCountEvent {
    private final Long postId;
    private final long delta;  // +1 또는 -N
}
