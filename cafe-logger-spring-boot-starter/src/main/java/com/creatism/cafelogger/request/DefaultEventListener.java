package com.creatism.cafelogger.request;

import com.creatism.cafelogger.core.CafeLoggerKafkaProducer;
import com.creatism.cafelogger.core.ParameterBinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Parameter;

/**
 * Cafe Logger default event listener
 * @author xuyu
 */
@Slf4j
public class DefaultEventListener {

    @Autowired
    ObjectMapper objectMapper;

    @Async("cafeLoggerEventThreadPool")
    @EventListener(CafeLoggerRequestEvent.class)
    public void handleCafeLoggerRequestEvent(CafeLoggerRequestEvent event) throws JsonProcessingException {
        ParameterBinding[] bindings = event.getParameterBindings();
        ParameterBinding binding;
        Parameter parameter;
        Object value;
        String body = null, response = null;
        for (int i = 0; i < bindings.length; i++) {
            binding = bindings[i];
            parameter = binding.getParameter();
            value = binding.getValue();
            if(parameter.getAnnotation(RequestBody.class) != null) {
                // deal as json
                body = objectMapper.writeValueAsString(value);
            }
        }
        response = objectMapper.writeValueAsString(event.getResponse());
        CafeLoggerRequestLog logInfo = CafeLoggerRequestLog.builder()
                .uri(event.getUri())
                .method(event.getMethod())
                .headers(String.valueOf(event.getHeaders()))
                .contentType(event.getContentType())
                .body(body)
                .response(response)
                .build();
        CafeLoggerKafkaProducer.send(event.getTopic(), objectMapper.writeValueAsString(logInfo));
    }
}
