package com.creatism.cafelogger.core;

import com.creatism.cafelogger.properties.CafeLoggerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.concurrent.atomic.AtomicInteger;

import static com.creatism.cafelogger.core.CafeLoggerConstants.CAFE_LOGGER_NAME;
import static com.creatism.cafelogger.core.CafeLoggerConstants.PCS_PREFIX;

/**
 * there are some benefits of using {@link CafeLoggerInterceptor} below:
 * <ul>
 * <li>To avoid resolving request content by using aspects to deal with function level information</li>
 * <li>support functions level information collection</li>
 * <li>focus on request body which the functions defined</li>
 * <li>ignore unexpected exception(because the exceptions may change kafka message format.)</li>
 * </ul>
 * Dynamic load cafe logger aspects by configuration.<br>
 * To make aspects effect, should use {@link BeanDefinitionRegistryPostProcessor} instead of {@link org.springframework.beans.factory.config.BeanPostProcessor}<br>
 * {@link EnvironmentAware} for reading configuration file,{@link ApplicationContext} for getting spring context(for current request)
 * @author xuyu
 */
@Slf4j
public class CafeLoggerBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {


    private Environment environment;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        try {
            BindResult<CafeLoggerProperties> restServiceBindResult = Binder.get(environment).bind(PCS_PREFIX, CafeLoggerProperties.class);
            CafeLoggerProperties properties = restServiceBindResult.get();
            CafeLoggerConfugurationHolder.setConfig(properties);
            AtomicInteger z = new AtomicInteger(1);
            // Dynamic weaving AOP with configuration
            properties.getLoggerPoints().stream().filter(i -> i.getPointcut()!=null).forEach(i -> {
                // spring boot just use aspectj to resolve and match aspect pointcut
                // if pattern of configuration is not null
                // try to weave aspect
                AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
                pointcut.setExpression(i.getPointcut());
                BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(DefaultPointcutAdvisor.class);
                builder.addConstructorArgValue(pointcut);
                builder.addConstructorArgValue(new CafeLoggerInterceptor(i.getTopic()));
                registry.registerBeanDefinition(CAFE_LOGGER_NAME + (z.getAndIncrement()), builder.getBeanDefinition());
            });
        } catch (Exception e) {
            log.error("cafe log config failed");
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setEnvironment(final Environment environment) {
        this.environment = environment;
    }
}
