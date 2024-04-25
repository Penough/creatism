package com.creatism.cafelogger.core;

import com.creatism.cafelogger.feign.CafeLoggerFeignInterceptor;
import com.creatism.cafelogger.properties.CafeLoggerPcsProperties;
import com.creatism.cafelogger.properties.CafeLoggerProperties;
import com.creatism.cafelogger.request.CafeLoggerRequestEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.creatism.cafelogger.core.CafeLoggerConstants.REFLECT_MAPPING_METHOD_PATH;
import static com.creatism.cafelogger.core.CafeLoggerConstants.REFLECT_MAPPING_METHOD_VALUE;

/**
 * init {@link CafeLoggerKafkaProducer} after initialization of {@link KafkaTemplate}
 *
// * @deprecated because of spring boot logger loading, cafe logger will override.So use {@link CafeLoggerApplicationRunner} to init.
 * @author xuyu
 */
@Slf4j
public class CafeLoggerBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware, EnvironmentAware {
    private Environment environment;
    private ApplicationContext context;
    /**
     * {@link RequestMapping} should rank last. The annotation can annotate method and class, the other can only annotate method.
     */
    private static final Class[] MAPPING_CLAZZ = {PostMapping.class, GetMapping.class, PutMapping.class, PatchMapping.class, RequestMapping.class};
    private static boolean checked = false;
    private static List<CafeLoggerPcsProperties> uriPcs = null;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();


    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // enhance controller method by uri pattern matched
        if(bean.getClass().getAnnotation(RestController.class) != null) {
            return enhanceController(bean);
        }
        if(bean instanceof OkHttpClient.Builder) {
            ((OkHttpClient.Builder)bean).addInterceptor(new CafeLoggerFeignInterceptor(context));
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }

    private Map<String, List<CafeLoggerPcsProperties>> matchRouterAndTopic(Object bean) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        RequestMapping baseMapping = bean.getClass().getAnnotation(RequestMapping.class);
        String[] baseUrls = {""};
        if(baseMapping != null) {
            baseUrls = Optional.ofNullable(baseMapping.value())
                    .orElse(baseMapping.path());
        }
        // just scan declared methods to avoid proxy super method
        Method[] methods = bean.getClass().getDeclaredMethods();
        Object mappingAno;
        String[] tmpUris = null;
        Map<String, List<CafeLoggerPcsProperties>> result = new ConcurrentHashMap<>(64);
        for (int i = 0; i < methods.length; i++) {
            Method tmpMethod = methods[i];
            // there is only one mapping class will work
            for (int j = 0; j < MAPPING_CLAZZ.length; j++) {
                mappingAno = tmpMethod.getAnnotation(MAPPING_CLAZZ[j]);
                if(mappingAno == null) {
                    continue;
                }
                tmpUris = Optional.ofNullable((String[])MAPPING_CLAZZ[j].getDeclaredMethod(REFLECT_MAPPING_METHOD_VALUE).invoke(mappingAno))
                        .orElse((String[])MAPPING_CLAZZ[j].getDeclaredMethod(REFLECT_MAPPING_METHOD_PATH).invoke(mappingAno));
                break;
            }
            String[] finalTmpUris = tmpUris;
            Arrays.stream(baseUrls)
                    .map(baseUrl -> Arrays.stream(finalTmpUris).map(uri -> baseUrl + uri).collect(Collectors.toList()))
                    .flatMap(Collection::stream)
                    .forEach(combination -> {
                        Optional<CafeLoggerPcsProperties> opt = uriPcs.stream().filter(pattern -> pathMatcher.match(pattern.getUri(), combination)).findFirst();
                        if(opt.isPresent()) {
                            String methodName = tmpMethod.getName();
                            if(!result.containsKey(methodName)) {
                                result.put(methodName, new ArrayList<>());
                            }
                            result.get(methodName).add(opt.get());
                        }
                    });
        }
        return result;
    }

    /**
     * enhance controller bean by cglib<br/>
     * one controller method may match multiple configure points for different topics and different paths
     * @param bean
     * @return
     * @throws BeansException
     */
    public Object enhanceController(Object bean) throws BeansException {
        if(checked && uriPcs==null) {
            return bean;
        }
        checked = true;
        CafeLoggerProperties properties = CafeLoggerConfugurationHolder.getConfig();
        if (properties==null) {
            return bean;
        }
        uriPcs = properties.getLoggerPoints().stream()
                .filter(i -> !StringUtils.isEmpty(i.getUri())).collect(Collectors.toList());
        if(uriPcs.size() < 1) {
            uriPcs = null;
        }
        Map<String, List<CafeLoggerPcsProperties>> matchResults = null;
        try {
            matchResults = matchRouterAndTopic(bean);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        if(matchResults.isEmpty()) {
            return bean;
        }
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(bean.getClass());

        Map<String, List<CafeLoggerPcsProperties>> finalMatchResults = matchResults;
        enhancer.setCallback((MethodInterceptor) (o, method, objects, methodProxy) -> {
            Object invoke = method.invoke(bean, objects);
            if(!finalMatchResults.containsKey(method.getName())){
                return invoke;
            }
            // match path again because of the probability of multiple path on one method
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();
            List<CafeLoggerPcsProperties> points = finalMatchResults.get(method.getName());
            CafeLoggerPcsProperties point;
            for (int i = 0; i < points.size(); i++) {
                point = points.get(i);
                if(!pathMatcher.match(point.getUri(), request.getRequestURI())) {
                    return invoke;
                }
                Enumeration names = request.getHeaderNames();
                Map headers = new HashMap(64);
                while(names.hasMoreElements()) {
                    String name = names.nextElement().toString();
                    Enumeration headerVals = request.getHeaders(name);
                    while(headerVals.hasMoreElements()) {
                        headers.put(name, headerVals.nextElement());
                    }
                }
                Parameter[] parameters = method.getParameters();
                ParameterBinding[] parameterBindings = new ParameterBinding[parameters.length];
                for (int j = 0; j < parameterBindings.length; j++) {
                    parameterBindings[j] = new ParameterBinding(parameters[j], objects[j]);
                }
                context.publishEvent(CafeLoggerRequestEvent.builder()
                        .topic(point.getTopic())
                        .method(request.getMethod())
                        .uri(request.getRequestURI())
                        .headers(headers)
                        .contentType(request.getContentType())
                        .parameterBindings(parameterBindings)
                        .response(invoke)
                        .build());
            }
            return invoke;
        });
        return enhancer.create();
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
