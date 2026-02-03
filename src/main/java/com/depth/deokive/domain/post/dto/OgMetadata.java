package com.depth.deokive.domain.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OgMetadata {
    private String title;
    private String imageUrl;
    private String description;
}
