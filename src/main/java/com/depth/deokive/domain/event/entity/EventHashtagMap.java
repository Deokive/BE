package com.depth.deokive.domain.event.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(
    name = "event_hashtag_map",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_hashtag", columnNames = {"event_id", "hashtag_id"})
    },
    indexes = { @Index(name = "idx_hashtag_event", columnList = "hashtag_id" ) }
)
public class EventHashtagMap {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hashtag_id", nullable = false)
    private Hashtag hashtag;
}