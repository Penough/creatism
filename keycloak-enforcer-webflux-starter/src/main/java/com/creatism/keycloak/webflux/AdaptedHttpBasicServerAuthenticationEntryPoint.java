package com.creatism.keycloak.webflux;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
/**
 *
 * @author penough
 */
public class AdaptedHttpBasicServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final String DEFAULT_REALM = "Realm";
    private static String WWW_AUTHENTICATE_FORMAT = "Basic realm=\"%s\"";
    private String headerValue = createHeaderValue(DEFAULT_REALM);

    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return Mono.fromRunnable(() -> {
            HttpStatus status = HttpStatus.UNAUTHORIZED;
            if(ex instanceof OAuth2AuthenticationException e) {
                status = HttpStatus.valueOf(e.getError().getErrorCode());
            }
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(status);
            response.getHeaders().set("WWW-Authenticate", this.headerValue);
        });
    }

    public void setRealm(String realm) {
        this.headerValue = createHeaderValue(realm);
    }

    private static String createHeaderValue(String realm) {
        Assert.notNull(realm, "realm cannot be null");
        return String.format(WWW_AUTHENTICATE_FORMAT, realm);
    }
}
