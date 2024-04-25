package com.creatism.cafelogger.core;


import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * <p>自定义拦截器处理需要发送到Kafka的日志</p>
 * <p>使用{@link MethodInterceptor}的好处在于不影响请求和响应解析过程，同时顺便省略去了该步骤，同时可以灵活指定织入切点</p>
 *
 * @author xuyu
 * @date 2023-4-7
 */
@Slf4j
public class CafeLoggerInterceptor implements MethodInterceptor {

    private final String topic;

    protected CafeLoggerInterceptor(String topic) {
        this.topic = topic;
    }

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        long st = System.currentTimeMillis();
        // invoking method may throw unhandled exceptions
        // just throw it out without handle
        Object res = methodInvocation.proceed();
        long cost = System.currentTimeMillis() - st;
        // 异步发送至kafka
        CafeLoggerKafkaProducer.asyncSend(methodInvocation, res, cost, topic);

        return res;
    }

}
