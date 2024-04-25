package com.creatism.cafelogger.feign;

import lombok.Builder;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

@Builder
@Data
public class CafeLoggerFeignLoggerEvent extends ApplicationEvent {
    public static final String DEFAULT_NAME = "__cafeLoggerFeignLoggerEvent__";
    private String topic;
    private String method;
    private String uri;
    private String headers;
    private String contentType;
    private String body;
    private String response;


    public CafeLoggerFeignLoggerEvent() {
        this(DEFAULT_NAME);
    }

    public CafeLoggerFeignLoggerEvent(String name) {
        super(name);
    }

    public CafeLoggerFeignLoggerEvent(String topic, String method, String uri, String headers, String contentType, String body, String response) {
        super(DEFAULT_NAME);
        this.topic = topic;
        this.method = method;
        this.uri = uri;
        this.headers = headers;
        this.contentType = contentType;
        this.body = body;
        this.response = response;
    }
}
