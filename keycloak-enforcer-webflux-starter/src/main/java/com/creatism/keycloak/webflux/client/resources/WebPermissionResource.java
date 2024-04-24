package com.creatism.keycloak.webflux.client.resources;

import com.creatism.keycloak.webflux.client.util.WebTokenCallable;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.representations.idm.authorization.PermissionRequest;
import org.keycloak.representations.idm.authorization.PermissionResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
/**
 *
 * @author penough
 */
public class WebPermissionResource {
    private final WebClient http;
    private final ServerConfiguration serverConfiguration;
    private final WebTokenCallable pat;

    public WebPermissionResource(WebClient http, ServerConfiguration serverConfiguration, WebTokenCallable pat) {
        this.http = http;
        this.serverConfiguration = serverConfiguration;
        this.pat = pat;
    }

    public Mono<PermissionResponse> create(PermissionRequest request) {
        return create(Arrays.asList(request));
    }

    public Mono<PermissionResponse> create(List<PermissionRequest> request) {
        return pat.call()
                .flatMap(token -> http.post().uri(serverConfiguration.getPermissionEndpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer" + token)
                        .body(Mono.just(request), PermissionRequest.class)
                        .retrieve()
                        .bodyToMono(PermissionResponse.class));
    }

}
