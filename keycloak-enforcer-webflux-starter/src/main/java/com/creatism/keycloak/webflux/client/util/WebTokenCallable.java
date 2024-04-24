package com.creatism.keycloak.webflux.client.util;

import com.creatism.keycloak.webflux.client.WebConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.common.util.Time;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.RefreshToken;
import org.keycloak.util.JsonSerialization;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Slf4j
public class WebTokenCallable implements Callable<Mono<String>> {
    private final String userName;
    private final String password;
    private final String scope;
    private final WebClient http;
    private final WebConfiguration configuration;
    private final ServerConfiguration serverConfiguration;
    private AccessTokenResponse tokenResponse;

    public WebTokenCallable(String userName, String password, String scope, WebClient http, WebConfiguration configuration,
                         ServerConfiguration serverConfiguration) {
        this.userName = userName;
        this.password = password;
        this.scope = scope;
        this.http = http;
        this.configuration = configuration;
        this.serverConfiguration = serverConfiguration;
    }

    public WebTokenCallable(String userName, String password, WebClient http, WebConfiguration configuration,
                         ServerConfiguration serverConfiguration) {
        this(userName, password, null, http, configuration, serverConfiguration);
    }

    public WebTokenCallable(WebClient http, WebConfiguration configuration, ServerConfiguration serverConfiguration) {
        this(null, null, http, configuration, serverConfiguration);
    }

    @Override
    public Mono<String> call() {
        if(tokenResponse != null) {
            return Mono.just(tokenResponse)
                    .map(atr -> validateRawAccessToken(atr.getToken()))
                    .switchIfEmpty(tryRefreshToken()
                            .map(rr -> {
                                tokenResponse = rr;
                                return tokenResponse.getToken();
                            }));
        }
        return obtainTokens()
                .map(atr -> atr.getToken());
    }


    private String validateRawAccessToken(String rawAccessToken) {
        try {
            AccessToken accessToken = JsonSerialization.readValue(new JWSInput(rawAccessToken).getContent(), AccessToken.class);
            if (accessToken.isActive() && this.isTokenTimeToLiveSufficient(accessToken)) {
                return rawAccessToken;
            } else {
                log.debug("Access token is expired.");
            }
        } catch (JWSInputException | IOException e) {
            clearTokens();
            throw new RuntimeException("Failed to parse access token", e);
        }
        return null;
    }

    private Mono<AccessTokenResponse> tryRefreshToken() {
        String rawRefreshToken = tokenResponse.getRefreshToken();

        if (rawRefreshToken == null) {
            log.debug("Refresh token not found, obtaining new tokens");
            return obtainTokens();
        }

        try {
            RefreshToken refreshToken = JsonSerialization.readValue(new JWSInput(rawRefreshToken).getContent(), RefreshToken.class);
            if (!refreshToken.isActive() || !isTokenTimeToLiveSufficient(refreshToken)) {
                log.debug("Refresh token is expired.");
                return obtainTokens();
            }
        } catch (Exception cause) {
            clearTokens();
            throw new RuntimeException("Failed to parse refresh token", cause);
        }

        return refreshToken(rawRefreshToken);
    }

    public boolean isTokenTimeToLiveSufficient(AccessToken token) {
        return token != null && (token.getExpiration() - getWebConfiguration().getTokenMinimumTimeToLive()) > Time.currentTime();
    }

    /**
     * Obtains an access token using the client credentials.
     *
     * @return an {@link AccessTokenResponse}
     */
    Mono<AccessTokenResponse> clientCredentialsGrant() {
        Map<String, String> headers = new LinkedHashMap();
        MultiValueMap params = new LinkedMultiValueMap();
        params.put(OAuth2Constants.GRANT_TYPE, Arrays.asList(OAuth2Constants.CLIENT_CREDENTIALS));
        configuration.getClientCredentialsProvider().setClientCredentials(configuration, headers, params);

        return this.http.post().uri(this.serverConfiguration.getTokenEndpoint())
                .headers(hs -> putMapIntoHttpHeaders(hs, headers))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .bodyToMono(AccessTokenResponse.class);
    }

    /**
     * Obtains an access token using the resource owner credentials.
     *
     * @return an {@link AccessTokenResponse}
     */
    Mono<AccessTokenResponse> resourceOwnerPasswordGrant(String userName, String password) {
        return resourceOwnerPasswordGrant(userName, password, null);
    }

    Mono<AccessTokenResponse> resourceOwnerPasswordGrant(String userName, String password, String scope) {
        Map<String, String> headers = new LinkedHashMap();
        MultiValueMap params = new LinkedMultiValueMap();
        params.put(OAuth2Constants.GRANT_TYPE, Arrays.asList(OAuth2Constants.PASSWORD));
        params.put("username",userName);
        params.put("password", password);
        if(scope != null) {
            params.put("scope", scope);
        }
        configuration.getClientCredentialsProvider().setClientCredentials(configuration, headers, params);

        return this.http.post().uri(this.serverConfiguration.getTokenEndpoint())
                .headers(hs -> putMapIntoHttpHeaders(hs, headers))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .bodyToMono(AccessTokenResponse.class);
    }

    private Mono<AccessTokenResponse> refreshToken(String rawRefreshToken) {
        log.debug("Refreshing tokens");
        Map<String, String> headers = new LinkedHashMap();
        MultiValueMap params = new LinkedMultiValueMap();
        params.put(OAuth2Constants.GRANT_TYPE, Arrays.asList(OAuth2Constants.CLIENT_CREDENTIALS));
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", rawRefreshToken);

        configuration.getClientCredentialsProvider().setClientCredentials(configuration, headers, params);

        return this.http.post().uri(this.serverConfiguration.getTokenEndpoint())
                .headers(hs -> putMapIntoHttpHeaders(hs, headers))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .bodyToMono(AccessTokenResponse.class);
    }

    private Mono<AccessTokenResponse> obtainTokens() {
        if (userName == null || password == null) {
            return clientCredentialsGrant();
        } else if (scope != null) {
            return resourceOwnerPasswordGrant(userName, password, scope);
        } else {
            return resourceOwnerPasswordGrant(userName, password);
        }
    }

    private void putMapIntoHttpHeaders(HttpHeaders hs, Map<String, String> headers) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            hs.put(e.getKey(), Arrays.asList(e.getValue()));
        }
    }

    WebClient getHttp() {
        return http;
    }

    protected boolean isRetry() {
        return true;
    }

    WebConfiguration getWebConfiguration() {
        return configuration;
    }

    ServerConfiguration getServerConfiguration() {
        return serverConfiguration;
    }

    void clearTokens() {
        tokenResponse = null;
    }

}
