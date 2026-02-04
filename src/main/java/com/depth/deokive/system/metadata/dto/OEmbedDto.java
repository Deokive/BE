package com.depth.deokive.system.metadata.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OEmbedDto {
    @Builder.Default private String type = "link"; // 봇이 레이아웃을 결정하는 기준
    @Builder.Default private String version = "1.0"; // OEmbed Protocol Version

    private String title;

    @JsonProperty("provider_name") private String providerName; // Service Name
    @JsonProperty("provider_url") private String providerUrl; // Service Main Page Link
    @JsonProperty("thumbnail_url") private String thumbnailUrl;
    @JsonProperty("thumbnail_width") private Integer thumbnailWidth;
    @JsonProperty("thumbnail_height") private Integer thumbnailHeight;
}