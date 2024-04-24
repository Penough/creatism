package com.creatism.keycloak.webflux;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.AuthorizationContext;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
/**
 *
 * @author penough
 */
@Slf4j
@RequiredArgsConstructor
public class PolicyEnforcerWebFilter implements WebFilter {
    private final WebPolicyEnforcer enforcer;
    private ServerAuthenticationFailureHandler authenticationFailureHandler = new ServerAuthenticationEntryPointFailureHandler(
            new AdaptedHttpBasicServerAuthenticationEntryPoint());

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return enforcer.enforce(exchange)
                .log()
                .switchIfEmpty(Mono.empty())
                .flatMap(ac -> filterAuthorizationContext(ac, exchange, chain))
                .onErrorResume(AuthenticationException.class, (ex) -> this.authenticationFailureHandler
                        .onAuthenticationFailure(new WebFilterExchange(exchange, chain), ex));
    }

    private Mono<Void> filterAuthorizationContext(AuthorizationContext ac, ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getAttributes().put(AuthorizationContext.class.getName(), ac);
        if(ac.isGranted()) {
            log.debug("Request authorized, continuing the filter chain");
            return chain.filter(exchange);
        }  else {
            log.debug("Unauthorized request to path {}, aborting the filter chain", exchange.getRequest().getURI());
            return Mono.empty();
        }
    }
}
