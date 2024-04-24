package com.creatism.keycloak.webflux.client;

import com.creatism.keycloak.webflux.GenericBodyExtractor;
import com.creatism.keycloak.webflux.client.resources.WebAuthorizationResource;
import com.creatism.keycloak.webflux.client.resources.WebProtectionResource;
import com.creatism.keycloak.webflux.client.util.WebTokenCallable;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.keycloak.constants.ServiceUrlConstants.AUTHZ_DISCOVERY_URL;
/**
 *
 * @author penough
 */
public class WebAuthzClient {
    private WebConfiguration configuration;
    private ServerConfiguration serverConfiguration;
    private WebClient http;
    private WebTokenCallable patSupplier;

    private BodyExtractor<Mono<ServerConfiguration>, ReactiveHttpInputMessage> bodyExtractor = new GenericBodyExtractor();

    public static WebAuthzClient create(WebConfiguration config) {
        return new WebAuthzClient(config);
    }

    public WebAuthzClient(WebConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("Client configuration can not be null.");
        }

        String configurationUrl = config.getAuthServerUrl();

        if (configurationUrl == null) {
            throw new IllegalArgumentException("Configuration URL can not be null.");
        }

        configurationUrl = KeycloakUriBuilder.fromUri(configurationUrl).clone().path(AUTHZ_DISCOVERY_URL).build(config.getRealm()).toString();
        this.configuration = config;

        this.http = WebClient.create();
        this.http.get().uri(configurationUrl)
                .retrieve()
                .bodyToMono(ServerConfiguration.class)
                .map(sc -> this.serverConfiguration = sc)
                .block();
    }

    public WebProtectionResource protection() {
        return new WebProtectionResource(this.http, this.serverConfiguration, configuration, createPatSupplier());
    }

    public WebAuthorizationResource authorization() {
        return new WebAuthorizationResource(configuration, serverConfiguration, this.http, null);
    }

    /**
     * <p>Creates a {@link WebAuthorizationResource} instance which can be used to obtain permissions from the server.
     *
     * @param accessToken the Access Token that will be used as a bearer to access the token endpoint
     * @return a {@link WebAuthorizationResource}
     */
    public WebAuthorizationResource authorization(final String accessToken) {
        return new WebAuthorizationResource(configuration, serverConfiguration, this.http, new WebTokenCallable(http, configuration, serverConfiguration) {
            @Override
            public Mono<String> call() {
                return Mono.just(accessToken);
            }

            @Override
            protected boolean isRetry() {
                return false;
            }
        });
    }

    private WebTokenCallable createPatSupplier(String userName, String password) {
        if (patSupplier == null) {
            patSupplier = createRefreshableAccessTokenSupplier(userName, password);
        }
        return patSupplier;
    }

    private WebTokenCallable createPatSupplier() {
        return createPatSupplier(null, null);
    }

    private WebTokenCallable createRefreshableAccessTokenSupplier(final String userName, final String password) {
        return createRefreshableAccessTokenSupplier(userName, password, null);
    }

    private WebTokenCallable createRefreshableAccessTokenSupplier(final String userName, final String password,
                                                               final String scope) {
        return new WebTokenCallable(userName, password, scope, http, configuration, serverConfiguration);
    }

    public WebConfiguration getConfiguration() {
        return configuration;
    }

    public ServerConfiguration getServerConfiguration() {
        return serverConfiguration;
    }
}
