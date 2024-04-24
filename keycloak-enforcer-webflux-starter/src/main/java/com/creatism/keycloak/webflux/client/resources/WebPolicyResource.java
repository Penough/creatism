package com.creatism.keycloak.webflux.client.resources;

import com.creatism.keycloak.webflux.client.util.WebTokenCallable;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.springframework.web.reactive.function.client.WebClient;
/**
 *
 * @author penough
 */
public class WebPolicyResource {
    private final WebClient http;
    private final ServerConfiguration serverConfiguration;
    private final WebTokenCallable pat;
    private final String resourceId;

    public WebPolicyResource(String resourceId, WebClient http, ServerConfiguration serverConfiguration, WebTokenCallable pat) {
        this.http = http;
        this.serverConfiguration = serverConfiguration;
        this.pat = pat;
        this.resourceId = resourceId;
    }
}
