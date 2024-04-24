package com.creatism.keycloak.webflux;

import com.creatism.keycloak.webflux.client.WebAuthzClient;
import org.keycloak.AuthorizationContext;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
/**
 *
 * @author penough
 */
public class WebClientAuthorizationContext extends AuthorizationContext {
    private final WebAuthzClient client;

    public WebClientAuthorizationContext(AccessToken authzToken, PolicyEnforcerConfig.PathConfig current, WebAuthzClient client) {
        super(authzToken, current);
        this.client = client;
    }

    public WebClientAuthorizationContext(WebAuthzClient client) {
        this.client = client;
    }

    public WebAuthzClient getClient() {
        return client;
    }
}
