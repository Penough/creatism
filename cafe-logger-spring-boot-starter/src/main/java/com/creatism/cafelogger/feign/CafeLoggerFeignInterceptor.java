package com.creatism.cafelogger.feign;

import com.creatism.cafelogger.core.CafeLoggerConfugurationHolder;
import com.creatism.cafelogger.properties.CafeLoggerPcsProperties;
import com.creatism.cafelogger.properties.CafeLoggerProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class CafeLoggerFeignInterceptor implements Interceptor {

    private ApplicationContext context;
    private static final Charset UTF8 = Charset.forName("utf-8");


    public CafeLoggerFeignInterceptor(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        String bodyString = resolveRequestBodyAsString(request.body());

        String responseString = resolveResponseBodyAsString(response.body());
        String headersString = request.headers().toString();

        CafeLoggerProperties properties = CafeLoggerConfugurationHolder.getConfig();
        List<CafeLoggerPcsProperties> points = properties.getLoggerPoints().stream()
                .filter(i -> i.getFeignUri() != null)
                .collect(Collectors.toList());
        CafeLoggerPcsProperties point;
        for (int i = 0; i < points.size(); i++) {
            point = points.get(i);
            context.publishEvent(CafeLoggerFeignLoggerEvent.builder()
                    .method(request.method())
                    .uri(request.url().uri().toString())
                    .topic(point.getTopic())
                    .contentType(request.body().contentType().type())
                    .body(bodyString)
                    .headers(headersString)
                    .response(responseString)
                    .build());
        }
        return response;
    }

    private String resolveRequestBodyAsString(RequestBody body) {
        if(body == null) {
            return null;
        }
        Buffer buffer = new Buffer();
        try {
            body.writeTo(buffer);
        } catch (IOException e) {
            log.error("feign request resolve failed");
            throw new RuntimeException(e);
        }
        return buffer.readString(UTF8);
    }

    private String resolveResponseBodyAsString(ResponseBody body) throws IOException {
        if(body == null) {
            return null;
        }
        BufferedSource source = body.source();
        source.request(Integer.MAX_VALUE);
        Buffer buffer = source.getBuffer();
        return buffer.clone().readString(UTF8);
    }
}
