package com.creatism.cafelogger.core;

import org.springframework.beans.BeansException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;

import static com.creatism.cafelogger.core.CafeLoggerConstants.APP_NAME_PROPERTY;

/**
 * add cafe logger into {@link ch.qos.logback.classic.LoggerContext} cache after spring boot starting
 * @author xuyu
 */
public class CafeLoggerApplicationRunner implements ApplicationRunner, EnvironmentAware, ApplicationContextAware {

    private Environment environment;
    private ApplicationContext applicationContext;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        Object kafBean = applicationContext.getBean(KafkaTemplate.class);
        String appName = environment.getProperty(APP_NAME_PROPERTY);
        CafeLoggerKafkaProducer.init((KafkaTemplate) kafBean, appName);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
