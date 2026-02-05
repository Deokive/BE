package com.depth.deokive.system.metadata.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShareMetadataDto {
    private String ogTitle;
    private String ogDescription;
    private String ogImage;
    private String redirectUrl;
}
