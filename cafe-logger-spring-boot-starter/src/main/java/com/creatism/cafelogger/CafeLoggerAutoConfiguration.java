package com.creatism.cafelogger;

import com.creatism.cafelogger.core.CafeLoggerApplicationRunner;
import com.creatism.cafelogger.core.CafeLoggerBeanDefinitionRegistryPostProcessor;
import com.creatism.cafelogger.core.CafeLoggerBeanPostProcessor;
import com.creatism.cafelogger.core.CafeLoggerKafkaProducer;
import com.creatism.cafelogger.feign.CafeLoggerFeignLoggerListener;
import com.creatism.cafelogger.request.DefaultEventListener;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static com.creatism.cafelogger.core.CafeLoggerConstants.THREAD_PREFIX;

/**
 * cafe logger autoconfiguration
 * <ul>
 *     <li>{@link CafeLoggerBeanDefinitionRegistryPostProcessor}</li>
 *     <li>{@link CafeLoggerBeanPostProcessor} for initialization of {@link CafeLoggerKafkaProducer}</li>
 * </ul>
 * @author xuyu
 */
@Configuration
@EnableAsync
@Import({DefaultEventListener.class, CafeLoggerFeignLoggerListener.class})
public class CafeLoggerAutoConfiguration {

    private static final int CPU_CORE_NUM = Runtime.getRuntime().availableProcessors();

    @Bean
    public BeanDefinitionRegistryPostProcessor cafeLoggerBeanDefinitionRegistryPostProcessor() {
        return new CafeLoggerBeanDefinitionRegistryPostProcessor();
    }

    @Bean
    public ApplicationRunner cafeLoggerApplicationRunner() {
        return new CafeLoggerApplicationRunner();
    }

    @Bean
    public CafeLoggerBeanPostProcessor cafeLoggerBeanPostProcessor() {
        return new CafeLoggerBeanPostProcessor();
    }

    @Bean
    @ConditionalOnMissingBean(OkHttpClient.Builder.class)
    public OkHttpClient.Builder feignClient() {
        return new OkHttpClient.Builder();
    }

    @Bean
    public Executor cafeLoggerEventThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CPU_CORE_NUM + 1);
        executor.setMaxPoolSize(CPU_CORE_NUM * 2);
        executor.setQueueCapacity(CPU_CORE_NUM * 10);
        executor.setThreadNamePrefix(THREAD_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();
        return executor;
    }

}
