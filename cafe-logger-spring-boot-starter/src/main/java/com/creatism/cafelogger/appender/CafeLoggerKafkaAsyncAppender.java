package com.creatism.cafelogger.appender;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * custom kafka appender
 * @author xuyu
 */
public class CafeLoggerKafkaAsyncAppender extends AsyncAppender {

    private static KafkaTemplate template;

    private String topic;

    @Override
    protected void append(ILoggingEvent eventObject) {
        String msg = eventObject.getFormattedMessage();
        template.send(topic, msg);
        super.append(eventObject);
    }

    private void setTopic(String topic) {
        this.topic = topic;
    }
}
