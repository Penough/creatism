package com.creatism.keycloak.webflux.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.http.client.HttpClient;
import org.keycloak.protocol.oidc.client.authentication.ClientCredentialsProvider;
import org.keycloak.protocol.oidc.client.authentication.ClientCredentialsProviderUtils;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
/**
 *
 * @author penough
 */
public class WebConfiguration extends AdapterConfig {

    @JsonIgnore
    private WebClient httpClient;

    @JsonIgnore
    private ClientCredentialsProvider clientCredentialsProvider;

    public WebConfiguration() {

    }

    /**
     * Creates a new instance.
     *
     * @param authServerUrl the server's URL. E.g.: http://{server}:{port}/auth.(not {@code null})
     * @param realm the realm name (not {@code null})
     * @param clientId the client id (not {@code null})
     * @param clientCredentials a map with the client credentials (not {@code null})
     * @param httpClient the {@link HttpClient} instance that should be used when sending requests to the server, or {@code null} if a default instance should be created
     */
    public WebConfiguration(String authServerUrl, String realm, String clientId, Map<String, Object> clientCredentials, WebClient httpClient) {
        this.authServerUrl = authServerUrl;
        setAuthServerUrl(authServerUrl);
        setRealm(realm);
        setResource(clientId);
        setCredentials(clientCredentials);
        this.httpClient = httpClient;
    }

    public WebClient getHttpClient() {
        if (this.httpClient == null) {
            this.httpClient = WebClient.create();
        }
        return httpClient;
    }

    public void setHttpClient(WebClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setClientCredentialsProvider(ClientCredentialsProvider clientCredentialsProvider) {
        this.clientCredentialsProvider = clientCredentialsProvider;
    }

    public ClientCredentialsProvider getClientCredentialsProvider() {
        if (clientCredentialsProvider == null) {
            clientCredentialsProvider = ClientCredentialsProviderUtils.bootstrapClientAuthenticator(this);
        }
        return clientCredentialsProvider;
    }
}