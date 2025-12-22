package com.depth.deokive.domain.event.entity;

import com.depth.deokive.domain.event.dto.EventDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name= "sport_record")
public class SportRecord {
    @Id
    private Long eventId;

    @Column(nullable = false)
    private String team1;

    @Column(nullable = false)
    private String team2;

    @Column(nullable = false)
    private Integer score1;

    @Column(nullable = false)
    private Integer score2;

    @MapsId // Event PK를 SportRecord의 PK로 사용
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    public void update(EventDto.SportRequest request) {
        this.team1 = nonBlankOrDefault(request.getTeam1(), this.team1);
        this.team2 = nonBlankOrDefault(request.getTeam2(), this.team2);
        this.score1 = nonBlankOrDefault(request.getScore1(), this.score1);
        this.score2 = nonBlankOrDefault(request.getScore2(), this.score2);
    }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}
