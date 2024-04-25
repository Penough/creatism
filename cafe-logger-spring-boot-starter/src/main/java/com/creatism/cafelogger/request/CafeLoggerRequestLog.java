package com.creatism.cafelogger.request;

import lombok.Builder;
import lombok.Data;

/**
 * @author xuyu
 */
@Data
@Builder
public class CafeLoggerRequestLog {
    private String method;
    private String uri;
    private String headers;
    private String contentType;
    private String body;
    private String response;
}
