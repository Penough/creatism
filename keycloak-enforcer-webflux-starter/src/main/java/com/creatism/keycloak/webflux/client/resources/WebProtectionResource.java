package com.creatism.keycloak.webflux.client.resources;

import com.creatism.keycloak.webflux.GenericBodyExtractor;
import com.creatism.keycloak.webflux.client.WebConfiguration;
import com.creatism.keycloak.webflux.client.util.WebTokenCallable;
import org.keycloak.OAuth2Constants;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.authorization.client.representation.TokenIntrospectionResponse;
import org.keycloak.authorization.client.resource.PermissionResource;
import org.keycloak.authorization.client.resource.ProtectedResource;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 *
 * @author penough
 */
public class WebProtectionResource {

    private final WebTokenCallable pat;
    private final WebClient http;
    private final WebConfiguration configuration;
    private ServerConfiguration serverConfiguration;
    private BodyExtractor<Mono<TokenIntrospectionResponse>, ReactiveHttpInputMessage> bodyExtractor = new GenericBodyExtractor();

    public WebProtectionResource(WebClient http, ServerConfiguration serverConfiguration, WebConfiguration configuration, WebTokenCallable pat) {
        if (pat == null) {
            throw new RuntimeException("No access token was provided when creating client for Protection API.");
        }

        this.http = http;
        this.serverConfiguration = serverConfiguration;
        this.configuration = configuration;
        this.pat = pat;
    }

    /**
     * Creates a {@link ProtectedResource} which can be used to manage resources.
     *
     * @return a {@link ProtectedResource}
     */
    public WebProtectedResource resource() {
        return new WebProtectedResource(http, serverConfiguration, configuration, pat);
    }


    /**
     * Creates a {@link PermissionResource} which can be used to manage permission tickets.
     *
     * @return a {@link PermissionResource}
     */
    public WebPermissionResource permission() {
        return new WebPermissionResource(http, serverConfiguration, pat);
    }

    public WebPolicyResource policy(String resourceId) {
        return new WebPolicyResource(resourceId, http, serverConfiguration, pat);
    }


    /**
     * Introspects the given <code>rpt</code> using the token introspection endpoint.
     *
     * @param rpt the rpt to introspect
     * @return the {@link TokenIntrospectionResponse}
     */
    public Mono<TokenIntrospectionResponse> introspectRequestingPartyToken(String rpt) {
        Map<String, String> headers = new LinkedHashMap();
        MultiValueMap params = new LinkedMultiValueMap();
        params.put(OAuth2Constants.GRANT_TYPE, Arrays.asList(OAuth2Constants.CLIENT_CREDENTIALS));
        params.put("token_type_hint", "requesting_party_token");
        params.put("token", rpt);
        configuration.getClientCredentialsProvider().setClientCredentials(configuration, headers, params);

        return this.http.post().uri(serverConfiguration.getIntrospectionEndpoint())
                .headers(hs -> {
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        hs.put(e.getKey(), Arrays.asList(e.getValue()));
                    }
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .bodyToMono(TokenIntrospectionResponse.class);
    }
}
