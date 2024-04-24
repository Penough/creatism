package com.creatism.keycloak.webflux.adapter.authorization.cip;

import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
/**
 *
 * @author penough
 */
public interface ReactiveClaimInformationPointProvider {

    String getName();

    Mono<Map<String, List<String>>> resolve(Map<String, Object> config, ServerHttpRequest request);
}
