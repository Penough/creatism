package com.creatism.cafelogger.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static com.creatism.cafelogger.core.CafeLoggerConstants.*;

/**
 * method level cafe logger interceptor
 * @author pengcheng
 */
public class CafeLoggerKafkaProducer {

    private static KafkaTemplate<String, Object> template;
    private static ThreadPoolTaskExecutor executor;
    private static String appName;
    private static Logger log;

    private static final ObjectMapper objectMapper = new ObjectMapper();


    private CafeLoggerKafkaProducer() {
    }

    public static void init(KafkaTemplate template, String serviceName) {
        CafeLoggerKafkaProducer.template = template;
        CafeLoggerKafkaProducer.appName = serviceName;
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(30);
        // 线程池对拒绝任务的处理策略
        // CallerRunsPolicy：由调用线程（提交任务的线程）处理该任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();


        String hostname = UNKNOWN;
        try {
            InetAddress ia = InetAddress.getLocalHost();
            hostname = ia.getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();

        String pathPrefix = CAFE_LOG_FILE_PATH
                + appName
                + FILE_SEP + hostname;
        if(loggerFactory instanceof LoggerContext) {
            LoggerContext loggerContext = (LoggerContext) loggerFactory;
            log = initLogback(loggerContext, pathPrefix);
        } else {
            log = null;
        }
    }


    public static void send(String topic, Object message) throws JsonProcessingException {
        String jsonStr = objectMapper.writeValueAsString(message);
        log.info(jsonStr);
        template.send(topic, message);
    }

    public static void asyncSend(String topic, String message) {
        executor.execute(() -> {
            log.info(message);
            template.send(topic, message);
        });
    }

    public static void asyncSend(MethodInvocation methodInvocation,
                          Object result, Long cost, String topic) {
        executor.execute(new AsyncLogMission(methodInvocation, result, cost, topic));
    }

    private static ch.qos.logback.classic.Logger initLogback(LoggerContext loggerContext, String pathPrefix) {

        // init logback configuration
        RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<ILoggingEvent>();
        rollingFileAppender.setContext(loggerContext);
        rollingFileAppender.setAppend(true);
        rollingFileAppender.setName(CAFE_FILE_LOGGER_NAME);
        rollingFileAppender.setFile(pathPrefix + FILE_SEP + CAFE_LOG_FILE_NAME);

        TimeBasedRollingPolicy rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setFileNamePattern(pathPrefix + FILE_SEP + CAFE_LOG_FILE_ROLLING_PATTERN);
        rollingPolicy.setContext(loggerContext);
        rollingPolicy.setParent(rollingFileAppender);
        rollingPolicy.start();
        rollingFileAppender.setRollingPolicy(rollingPolicy);

        //内容配置
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(LOG_MSG_PATTERN);
        encoder.setCharset(Charset.forName("UTF-8"));
        encoder.setContext(loggerContext);
        encoder.start();

        rollingFileAppender.setEncoder(encoder);
        rollingFileAppender.start();

        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(CAFE_ROOT_LOGGER);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(rollingFileAppender);
        rootLogger.setAdditive(false);
        return rootLogger;
    }


    @AllArgsConstructor
    static class AsyncLogMission implements Runnable {
        private final MethodInvocation methodInvocation;
        private final Object result;
        private final Long cost;
        private final String topic;


        @Override
        public void run() {
            Object[] params = methodInvocation.getArguments();
            Map jsonLog = new LinkedHashMap<>();
            String resStr = String.valueOf(result);
            jsonLog.put("result", resStr);
            StringBuffer paramsSb = new StringBuffer();
            AtomicInteger pidx = new AtomicInteger(0);
            Arrays.stream(methodInvocation.getMethod().getParameters()).forEach(p -> {
                paramsSb.append(p.getName()
                        + CafeLoggerConstants.COLON
                        + String.valueOf(params[pidx.getAndIncrement()])
                        + CafeLoggerConstants.CRLF);
            });
            jsonLog.put("params", paramsSb.toString());

            // 本地记录
            try {
                String jsonStr = objectMapper.writeValueAsString(jsonLog);
                log.info(jsonStr);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            template.send(topic, jsonLog);
        }
    }
}
