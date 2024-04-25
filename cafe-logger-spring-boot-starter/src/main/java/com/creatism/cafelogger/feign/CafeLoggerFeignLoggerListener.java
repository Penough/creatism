package com.creatism.cafelogger.feign;

import com.creatism.cafelogger.core.CafeLoggerKafkaProducer;
import com.creatism.cafelogger.request.CafeLoggerRequestLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 *
 * @author xuyu
 */
public class CafeLoggerFeignLoggerListener {

    @Autowired
    ObjectMapper objectMapper;

    @Async("cafeLoggerEventThreadPool")
    @EventListener(CafeLoggerFeignLoggerEvent.class)
    public void handleCafeLoggerFeignLoggerEvent(CafeLoggerFeignLoggerEvent event) throws JsonProcessingException {
        CafeLoggerRequestLog logInfo = CafeLoggerRequestLog.builder()
                .uri(event.getUri())
                .method(event.getMethod())
                .headers(String.valueOf(event.getHeaders()))
                .contentType(event.getContentType())
                .body(event.getBody())
                .response(event.getResponse())
                .build();
        CafeLoggerKafkaProducer.send(event.getTopic(), objectMapper.writeValueAsString(logInfo));
    }
}
