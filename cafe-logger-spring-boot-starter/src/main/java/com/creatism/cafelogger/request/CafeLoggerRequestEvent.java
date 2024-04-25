package com.creatism.cafelogger.request;

import com.creatism.cafelogger.core.ParameterBinding;
import lombok.Builder;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

@Data
@Builder
public class CafeLoggerRequestEvent extends ApplicationEvent {
    public static final String DEFAULT_NAME = "__cafeLoggerRequestEvent__";
    private String topic;
    private String method;
    private String uri;
    private Map headers;
    private String contentType;
    private ParameterBinding[] parameterBindings;
    private Object response;

    public CafeLoggerRequestEvent() {
        this(DEFAULT_NAME);
    }

    public CafeLoggerRequestEvent(String name) {
        super(name);
    }

    public CafeLoggerRequestEvent(String topic, String method, String uri, Map headers, String contentType, ParameterBinding[] parameterBindings, Object response) {
        super(DEFAULT_NAME);
        this.topic = topic;
        this.method = method;
        this.uri = uri;
        this.headers = headers;
        this.contentType = contentType;
        this.parameterBindings = parameterBindings;
        this.response = response;
    }
}
