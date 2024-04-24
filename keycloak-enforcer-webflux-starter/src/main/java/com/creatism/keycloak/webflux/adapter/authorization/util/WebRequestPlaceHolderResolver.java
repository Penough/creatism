package com.creatism.keycloak.webflux.adapter.authorization.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.creatism.keycloak.webflux.adapter.authorization.util.PlaceHoldersUtil.getParameter;

/**
 * @author penough
 */
@Slf4j
public class WebRequestPlaceHolderResolver implements WebPlaceHolderResolver {

    @Override
    public String getName() {
        return "request";
    }

    @Override
    public List<String> resolve(String placeHolder, ServerHttpRequest request) {
        // split regex like {resolverName.xxxxx}
        String source = placeHolder.substring(placeHolder.indexOf('.') + 1);
        // match {request.parameter[xxxx]} pattern
        if(source.startsWith("parameter[")) {
            String parameterName = getParameter(source, "Could not obtain parameter name from placeholder [" + source + "]");
            String parameterValue = request.getQueryParams().getFirst(parameterName);
            if (parameterValue != null) {
                return Arrays.asList(parameterValue);
            }
        } else if(source.startsWith("header[")) {
            String parameterName = getParameter(source, "Could not obtain parameter name from placeholder [" + source + "]");
            return Optional.ofNullable(request.getHeaders().get(parameterName)).orElse(Collections.emptyList());
        } else if (source.startsWith("cookie")) {
            String cookieName = getParameter(source, "Could not obtain cookie name from placeholder [" + source + "]");
            HttpCookie cookie = request.getCookies().getFirst(cookieName);
            if (cookie != null) {
                return Arrays.asList(cookie.getValue());
            }
        } else if (source.startsWith("remoteAddr")) {
            InetSocketAddress remoteAddress = request.getRemoteAddress();
            if (remoteAddress != null) {
                return Arrays.asList(remoteAddress.toString());
            }
        } else if (source.startsWith("method")) {
            return Arrays.asList(request.getMethod().toString());
        } else if (source.startsWith("uri")) {
            return Arrays.asList(request.getURI().toString());
        } else if (source.startsWith("relativePath")) {
            return Arrays.asList(request.getPath().toString());
        } else if (source.startsWith("secure")) {
            boolean isSecure = request.getSslInfo() != null;
            return Arrays.asList(String.valueOf(isSecure));
        } else if (source.startsWith("body")) {
            MediaType mt = request.getHeaders().getContentType();
            String path = getParameter(source, null);
            if(mt.equals(MediaType.APPLICATION_JSON)) {
                StringDecoder stringDecoder = StringDecoder.allMimeTypes();
                Mono<DataBuffer> m = DataBufferUtils.join(request.getBody());
                return m.map(buffer -> stringDecoder.decode(buffer, null, mt, null))
                        .map(s -> {
                            try {
                                JsonNode node = JsonUtils.readTree(s);
                                if(path == null) {
                                    return JsonUtils.getValues(node);
                                }
                                return JsonUtils.getValues(node, path);
                            } catch (JsonProcessingException e) {
                                log.error("parse json error :{}", e.getMessage());
                                return null;
                            }
                        }).block();
            }
        }
        return Collections.emptyList();
    }



}
