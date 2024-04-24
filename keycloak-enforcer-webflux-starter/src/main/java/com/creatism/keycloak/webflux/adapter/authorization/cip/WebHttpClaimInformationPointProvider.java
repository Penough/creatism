package com.creatism.keycloak.webflux.adapter.authorization.cip;

import com.creatism.keycloak.webflux.adapter.authorization.util.JsonUtils;
import com.creatism.keycloak.webflux.adapter.authorization.util.WebPlaceHolders;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.HttpResponseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;
/**
 *
 * @author penough
 */
public class WebHttpClaimInformationPointProvider implements ReactiveClaimInformationPointProvider {

    private final WebClient httpClient;
    private final WebPlaceHolders webPlaceHolders;

    public WebHttpClaimInformationPointProvider(WebPlaceHolders webPlaceHolders) {
        this.httpClient = WebClient.create();
        this.webPlaceHolders = webPlaceHolders;
    }

    @Override
    public String getName() {
        return "http";
    }

    @Override
    public Mono<Map<String, List<String>>> resolve(Map<String, Object> config, ServerHttpRequest request) {
            return executeRequest(config, request)
                    .map(jsonNode -> {
                        Map<String, List<String>> claims = new HashMap<>();
                        Map<String, Object> claimsDef = (Map<String, Object>) config.get("claims");
                        if (claimsDef == null) {
                            Iterator<String> nodeNames = jsonNode.fieldNames();

                            while (nodeNames.hasNext()) {
                                String nodeName = nodeNames.next();
                                claims.put(nodeName, JsonUtils.getValues(jsonNode.get(nodeName)));
                            }
                        } else {
                            for (Map.Entry<String, Object> claimDef : claimsDef.entrySet()) {
                                List<String> jsonPaths = new ArrayList<>();

                                if (claimDef.getValue() instanceof Collection) {
                                    jsonPaths.addAll(Collection.class.cast(claimDef.getValue()));
                                } else {
                                    jsonPaths.add(claimDef.getValue().toString());
                                }

                                List<String> claimValues = new ArrayList<>();

                                for (String path : jsonPaths) {
                                    claimValues.addAll(JsonUtils.getValues(jsonNode, path));
                                }

                                claims.put(claimDef.getKey(), claimValues);
                            }
                        }

                        return claims;
                    })
                    .onErrorResume(error -> Mono.error(new RuntimeException("Could not obtain claims from http claim information point [" + config.get("url") + "] response", error.getCause())));
    }

    private Mono<JsonNode> executeRequest(Map<String, Object> config, ServerHttpRequest request) {

        HttpMethod method = HttpMethod.valueOf(Optional.ofNullable(String.valueOf(config.get("method")))
                .orElse(HttpMethod.GET.name()));
        WebClient.RequestBodyUriSpec spec = httpClient.method(method);
        try {
            URI uri;
            List<NameValuePair> params = generateParameters(config, request);
            // only support GET and POST
            MultiValueMap formData = null;
            if(HttpMethod.GET.equals(method)) {
                String query = generateQueryParams(params);
                uri = new URI(String.valueOf(config.get("url")) + '?' + query );
            } else {
                formData = generateUeFormParams(params);
                uri = new URI(String.valueOf(config.get("url")));
            }
            spec.uri(uri);

            List<NameValuePair> headers = null;
            if (config.containsKey("headers")) {
                headers = generateHeaders(config, request);
            }

            if(!CollectionUtils.isEmpty(headers)) {
                headers.forEach(h -> spec.header(h.getName(), h.getValue()));
            }
            if(!CollectionUtils.isEmpty(formData)) {
                spec.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .body(BodyInserters.fromFormData(formData));
            }

        } catch (Exception cause) {
            throw new RuntimeException("Error construct claims information requests: " + cause.getMessage());
        }
        return spec.retrieve()
                .onRawStatus(code -> code < 200 || code >= 300,
                        res -> Mono.error(new HttpResponseException(401, "Unexpected response from server: " + res.statusCode())))
                .bodyToMono(JsonNode.class)
                .onErrorResume(error -> Mono.error(error));
    }

    private List<NameValuePair> generateHeaders(Map<String, Object> config, ServerHttpRequest request) {
        Object headersDef = config.get("headers");

        if (headersDef != null) {
            List<NameValuePair> hs = new ArrayList<>();
            Map<String, Object> headers = Map.class.cast(headersDef);

            for (Map.Entry<String, Object> header : headers.entrySet()) {
                Object value = header.getValue();
                List<String> headerValues = new ArrayList<>();

                if (value instanceof Collection) {
                    Collection values = Collection.class.cast(value);

                    for (Object item : values) {
                        headerValues.addAll(webPlaceHolders.resolve(item.toString(), request));
                    }
                } else {
                    headerValues.addAll(webPlaceHolders.resolve(value.toString(), request));
                }

                for (String headerValue : headerValues) {
                    hs.add(new NameValuePair(header.getKey(), headerValue));
                }
            }
        }
        return Collections.emptyList();
    }

    private List<NameValuePair> generateParameters(Map<String, Object> parConfig, ServerHttpRequest request) {
        Object config = parConfig.get("parameters");

        if (config != null) {
            Map<String, Object> paramsDef = Map.class.cast(config);
            List<NameValuePair> params = new ArrayList<>();
            for (Map.Entry<String, Object> paramDef : paramsDef.entrySet()) {
                Object value = paramDef.getValue();
                List<String> paramValues = new ArrayList<>();

                if (value instanceof Collection) {
                    Collection values = Collection.class.cast(value);

                    for (Object item : values) {
                        paramValues.addAll(webPlaceHolders.resolve(item.toString(), request));
                    }
                } else {
                    paramValues.addAll(webPlaceHolders.resolve(value.toString(), request));
                }

                for (String paramValue : paramValues) {
                    params.add(new NameValuePair(paramDef.getKey(), paramValue));
                }

            }
        }
        return Collections.emptyList();
    }

    private String generateQueryParams(List<NameValuePair> params) {
        StringBuilder qry = new StringBuilder();
        params.forEach(p -> qry.append(p.toString() + '&'));
        if(qry.length() > 0) {
            qry.deleteCharAt(qry.length() - 1);
        }
        return qry.toString();
    }

    private MultiValueMap generateUeFormParams(List<NameValuePair> params) {
        MultiValueMap form = new LinkedMultiValueMap();
        params.forEach(i -> form.put(i.getName(), i.getValue()));
        return form;
    }
}
