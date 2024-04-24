package com.creatism.keycloak.webflux;

import com.creatism.keycloak.webflux.adapter.authorization.cip.ReactiveClaimInformationPointProvider;
import com.creatism.keycloak.webflux.adapter.authorization.util.JsonUtils;
import com.creatism.keycloak.webflux.client.WebAuthzClient;
import com.creatism.keycloak.webflux.client.WebConfiguration;
import com.creatism.keycloak.webflux.client.WebPathConfigMatcher;
import com.creatism.keycloak.webflux.client.resources.WebPermissionResource;
import com.creatism.keycloak.webflux.client.resources.WebProtectionResource;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.AuthorizationContext;
import org.keycloak.authorization.client.AuthorizationDeniedException;
import org.keycloak.common.util.Base64;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.EnforcementMode;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.MethodConfig;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.PathConfig;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.Permission;
import org.keycloak.representations.idm.authorization.PermissionRequest;
import org.keycloak.util.JsonSerialization;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;
/**
 *
 * @author penough
 */
@Slf4j
public class WebPolicyEnforcer {

    private WebPolicyEnforcerProperties config;
    private WebAuthzClient webAuthzClient;
    private ServerOAuth2AuthorizedClientRepository clientRepository;
    private WebPathConfigMatcher webPathConfigMatcher;
    private Map<String, ReactiveClaimInformationPointProvider> claimProviders;
    static final String NO_VALID_ACCESS_TOKEN_FOUND = "no_valid_access_token_found";

    public WebPolicyEnforcer(WebPolicyEnforcerProperties config, ServerOAuth2AuthorizedClientRepository clientRepository,
                             Map<String, ReactiveClaimInformationPointProvider> claimProviders) {
        this.config = config;

        WebConfiguration webAuthzClientConfig = new WebConfiguration();
        webAuthzClientConfig.setRealm(config.getRealm());
        webAuthzClientConfig.setAuthServerUrl(config.getAuthServerUrl());
        webAuthzClientConfig.setCredentials(config.getCredentials());
        webAuthzClientConfig.setResource(config.getResource());

        this.webAuthzClient = WebAuthzClient.create(webAuthzClientConfig);
        this.webPathConfigMatcher = new WebPathConfigMatcher(config, webAuthzClient);
        this.clientRepository = clientRepository;
        this.claimProviders = claimProviders;
    }

    public Mono<AuthorizationContext> enforce(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        log.debug("Policy enforcement is enabled. Enforcing policy decisions for path [{0}].", request.getURI());
        return authorize(exchange)
                .map(context -> {
                    log.debug("Policy enforcement result for path [{0}] is : {1}", request.getURI(), context.isGranted() ? "GRANTED" : "DENIED");
                    log.debug("Returning authorization context with permissions:");
                    for (Permission permission : context.getPermissions()) {
                        log.debug(permission.toString());
                    }
                    return context;
                });
    }

    public Mono<AuthorizationContext> authorize(ServerWebExchange exchange) {
        EnforcementMode enforcementMode = config.getEnforcementMode();

        // disabled mode
        if (EnforcementMode.DISABLED.equals(enforcementMode)) {
            return exchange.getPrincipal()
                    .cast(Authentication.class)
                    .flatMap(au -> obtainAccessTokenString(au, exchange))
                    .switchIfEmpty(oauth2AuthenticationException(HttpStatus.UNAUTHORIZED.name()))
                    .map(ts -> createEmptyAuthorizationContext(true));
        }

        // PathConfig
        ServerHttpRequest request = exchange.getRequest();

        return getPathConfig(request)
                .flatMap(pathConfig -> generateAuthorizationContext(enforcementMode, pathConfig, exchange))
                .switchIfEmpty(doWithEmptyPathConfig(enforcementMode, exchange));
    }

    private Mono<AuthorizationContext> doWithEmptyPathConfig(EnforcementMode enforcementMode, ServerWebExchange exchange) {
        return obtainAccessTokenStringFromExchange(exchange)
                .flatMap(raw -> doWithAccessTokenEmptyPathConfig(enforcementMode, raw, exchange))
                .switchIfEmpty(handleAccessDenied(exchange.getResponse()));
    }

    private Mono<AuthorizationContext> doWithAccessTokenEmptyPathConfig(EnforcementMode enforcementMode, String rawAccessToken,
                                                                        ServerWebExchange exchange) {
        AccessToken accessToken = JsonUtils.asAccessToken(rawAccessToken);
        if (EnforcementMode.PERMISSIVE.equals(enforcementMode)) {
            return Mono.just(createAuthorizationContext(accessToken, null));
        }

        log.debug("Could not find a configuration for path {}", exchange.getRequest().getPath());
        if (isDefaultAccessDeniedUri(exchange.getRequest())) {
            return Mono.just(createAuthorizationContext(accessToken, null));
        }
        return handleAccessDenied(exchange.getResponse());
    }

    private Mono<String> obtainAccessTokenStringFromExchange(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .cast(Authentication.class)
                .flatMap(au -> obtainAccessTokenString(au, exchange));
    }

    private Mono<AuthorizationContext> generateAuthorizationContext(EnforcementMode enforcementMode, PathConfig pathConfig,
                                                                    ServerWebExchange exchange) {
        return obtainAccessTokenStringFromExchange(exchange)
                .flatMap(ts -> checkingPermissionForPath(pathConfig, ts, exchange.getRequest()))
                .switchIfEmpty(generateAnonymousAuthorizationContext(pathConfig, exchange.getRequest(), exchange.getResponse()));
    }

    private Mono<String> obtainAccessTokenString(Authentication authentication, ServerWebExchange exchange) {
        if(authentication instanceof OAuth2AuthenticationToken) {
            return clientRepository.loadAuthorizedClient(config.getClientRegistryId(), authentication, exchange)
                    .map(i -> i.getAccessToken().getTokenValue());
        } else if (authentication instanceof JwtAuthenticationToken token){
            return Mono.just(token.getToken().getTokenValue());
        }
        return Mono.empty();
    }


    private <T> Mono<T> oauth2AuthenticationException(String errorCode) {
        return Mono.defer(() -> {
            OAuth2Error oauth2Error = new OAuth2Error(errorCode);
            return Mono.error(new OAuth2AuthenticationException(oauth2Error));
        });
    }

    private Mono<PathConfig> getPathConfig(ServerHttpRequest request) {
        return isDefaultAccessDeniedUri(request) ? Mono.empty() : webPathConfigMatcher.matchPath(request.getPath().toString());
    }

    private boolean isDefaultAccessDeniedUri(ServerHttpRequest request) {
        String accessDeniedPath = config.getOnDenyRedirectTo();
        return accessDeniedPath != null && request.getURI().toString().contains(accessDeniedPath);
    }

    private Mono<AuthorizationContext> generateAnonymousAuthorizationContext(PathConfig pathConfig, ServerHttpRequest request,
                                                                             ServerHttpResponse response) {
        if (!isDefaultAccessDeniedUri(request)) {
            if (pathConfig != null) {
                if (EnforcementMode.DISABLED.equals(pathConfig.getEnforcementMode())) {
                    return Mono.just(createEmptyAuthorizationContext(true));
                } else {
                    challenge(pathConfig, getRequiredScopes(pathConfig, request), request, response);
                }
            } else {
                return handleAccessDenied(response);
            }
        }
        return Mono.just(createEmptyAuthorizationContext(false));
    }

    private Mono<AuthorizationContext> checkingPermissionForPath(PathConfig pathConfig,
                                                                 String rawAccessToken, ServerHttpRequest request) {
        log.debug("Checking permissions for path {} with config {}.", request.getURI(), pathConfig);
        AccessToken accessToken = JsonUtils.asAccessToken(rawAccessToken);

        if (EnforcementMode.DISABLED.equals(pathConfig.getEnforcementMode())) {
            return Mono.just(createAuthorizationContext(accessToken, pathConfig));
        }
        MethodConfig methodConfig = getRequiredScopes(pathConfig, request);
        return resolveClaims(pathConfig, request)
                .flatMap(claims -> {
                    if(isAuthorized(pathConfig, methodConfig, accessToken, request, claims)) {
                        return Mono.just(createAuthorizationContext(accessToken, pathConfig));
                    }
                    return requestAuthorizationToken(accessToken, rawAccessToken, pathConfig, methodConfig, request, claims)
                            .map(ac -> {
                                AccessToken.Authorization authorization = accessToken.getAuthorization();
                                if (authorization == null) {
                                    authorization = new AccessToken.Authorization();
                                    authorization.setPermissions(new ArrayList<Permission>());
                                }
                                AccessToken.Authorization newAuthorization = ac.getAuthorization();

                                if (newAuthorization != null) {
                                    Collection<Permission> grantedPermissions = authorization.getPermissions();
                                    Collection<Permission> newPermissions = newAuthorization.getPermissions();

                                    for (Permission newPermission : newPermissions) {
                                        if (!grantedPermissions.contains(newPermission)) {
                                            grantedPermissions.add(newPermission);
                                        }
                                    }
                                }
                                accessToken.setAuthorization(authorization);

                                if (isAuthorized(pathConfig, methodConfig, accessToken, request, claims)) {
                                    return createAuthorizationContext(accessToken, pathConfig);
                                }
                                return tailContext(methodConfig);
                            })
                            .switchIfEmpty(Mono.just(tailContext(methodConfig)));
                });
    }

    protected Mono<Void> challenge(PathConfig pathConfig, MethodConfig methodConfig, ServerHttpRequest request,
                                   ServerHttpResponse response) {
        if (isBearerAuthorization(request)) {
            return getPermissionTicket(pathConfig, methodConfig, webAuthzClient, request)
                    .switchIfEmpty(oauth2AuthenticationException(HttpStatus.FORBIDDEN.name()))
                    .map(ticket -> {
                        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, new StringBuilder("UMA realm=\"").append(webAuthzClient.getConfiguration().getRealm()).append("\"").append(",as_uri=\"")
                                .append(webAuthzClient.getServerConfiguration().getIssuer()).append("\"").append(",ticket=\"").append(ticket).append("\"").toString());
                        OAuth2Error oauth2Error = new OAuth2Error(HttpStatus.UNAUTHORIZED.name());
                        log.debug("Sending challenge");
                        throw new OAuth2AuthenticationException(oauth2Error);
                    });
        }
        return handleAccessDenied(response);
    }

    private AuthorizationContext tailContext(MethodConfig methodConfig) {
        if (methodConfig != null && PolicyEnforcerConfig.ScopeEnforcementMode.DISABLED.equals(methodConfig.getScopesEnforcementMode())) {
            return createEmptyAuthorizationContext(true);
        }
        return createEmptyAuthorizationContext(false);
    }

    private boolean isBearerAuthorization(ServerHttpRequest request) {
        List<String> authHeaders = request.getHeaders().get("Authorization");

        if (authHeaders != null) {
            for (String authHeader : authHeaders) {
                String[] split = authHeader.trim().split("\\s+");
                if (split == null || split.length != 2) {
                    continue;
                }
                if (!split[0].equalsIgnoreCase("Bearer")) {
                    continue;
                }
                return true;
            }
        }

        return webAuthzClient.getConfiguration().isBearerOnly();
    }

    protected boolean isAuthorized(PathConfig actualPathConfig, MethodConfig methodConfig, AccessToken accessToken, ServerHttpRequest request, Map<String, List<String>> claims) {
        if (isDefaultAccessDeniedUri(request)) {
            return true;
        }

        AccessToken.Authorization authorization = accessToken.getAuthorization();

        if (authorization == null) {
            return false;
        }

        boolean hasPermission = false;
        Collection<Permission> grantedPermissions = authorization.getPermissions();

        for (Permission permission : grantedPermissions) {
            if (permission.getResourceId() != null) {
                if (isResourcePermission(actualPathConfig, permission)) {
                    hasPermission = true;

                    if (actualPathConfig.isInstance() && !matchResourcePermission(actualPathConfig, permission)) {
                        continue;
                    }

                    if (hasResourceScopePermission(methodConfig, permission)) {
                        log.debug("Authorization GRANTED for path {}. Permissions {}.", actualPathConfig, grantedPermissions);
                        if (HttpMethod.DELETE.equals(request.getMethod()) && actualPathConfig.isInstance()) {
                            webPathConfigMatcher.removeFromCache(request.getPath().toString());
                        }

                        return hasValidClaims(permission, claims);
                    }
                }
            } else {
                if (hasResourceScopePermission(methodConfig, permission)) {
                    return true;
                }
            }
        }

        if (!hasPermission && EnforcementMode.PERMISSIVE.equals(actualPathConfig.getEnforcementMode())) {
            return true;
        }
        log.debug("Authorization FAILED for path {}. Not enough permissions {}.", actualPathConfig, grantedPermissions);

        return false;
    }

    private Mono<AccessToken> requestAuthorizationToken(AccessToken accessToken, String rawAccessToken,
                                                  PathConfig pathConfig, MethodConfig methodConfig,
                                                  ServerHttpRequest request, Map<String, List<String>> claims) {
        if (config.getUserManagedAccess() != null) {
            return Mono.empty();
        }

        AuthorizationRequest authzRequest = new AuthorizationRequest();

        if (isBearerAuthorization(request) || accessToken.getAuthorization() != null) {
            authzRequest.addPermission(pathConfig.getId(), methodConfig.getScopes());
        }
        if (!claims.isEmpty()) {
            authzRequest.setClaimTokenFormat("urn:ietf:params:oauth:token-type:jwt");
            try {
                authzRequest.setClaimToken(Base64.encodeBytes(JsonSerialization.writeValueAsBytes(claims)));
            } catch (IOException e) {
                log.error("Authentication failed: {}", e);
                return Mono.empty();
            }
        }

        if (accessToken.getAuthorization() != null) {
            authzRequest.setRpt(rawAccessToken);
        }

        log.debug("Obtaining authorization for authenticated user.");
        if (isBearerAuthorization(request)) {
            authzRequest.setSubjectToken(rawAccessToken);
            return webAuthzClient.authorization().authorize(authzRequest)
                    .map(resp -> JsonUtils.asAccessToken(resp.getToken()))
                    .onErrorResume(AuthorizationDeniedException.class, cause -> Mono.error(cause));
        } else {
            return webAuthzClient.authorization(rawAccessToken).authorize(authzRequest)
                    .map(resp -> JsonUtils.asAccessToken(resp.getToken()))
                    .onErrorResume(AuthorizationDeniedException.class, cause -> Mono.error(cause));
        }
    }

    private boolean hasValidClaims(Permission permission, Map<String, List<String>> claims) {
        Map<String, Set<String>> grantedClaims = permission.getClaims();

        if (grantedClaims != null) {
            if (claims.isEmpty()) {
                return false;
            }

            for (Map.Entry<String, Set<String>> entry : grantedClaims.entrySet()) {
                List<String> requestClaims = claims.get(entry.getKey());

                if (requestClaims == null || requestClaims.isEmpty() || !entry.getValue().containsAll(requestClaims)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isResourcePermission(PathConfig actualPathConfig, Permission permission) {
        // first we try a match using resource id
        boolean resourceMatch = matchResourcePermission(actualPathConfig, permission);

        // as a fallback, check if the current path is an instance and if so, check if parent's id matches the permission
        if (!resourceMatch && actualPathConfig.isInstance()) {
            resourceMatch = matchResourcePermission(actualPathConfig.getParentConfig(), permission);
        }

        return resourceMatch;
    }

    private boolean matchResourcePermission(PathConfig actualPathConfig, Permission permission) {
        return permission.getResourceId().equals(actualPathConfig.getId());
    }

    private boolean hasResourceScopePermission(MethodConfig methodConfig, Permission permission) {
        List<String> requiredScopes = methodConfig.getScopes();
        Set<String> allowedScopes = permission.getScopes();

        if (allowedScopes.isEmpty()) {
            return true;
        }

        PolicyEnforcerConfig.ScopeEnforcementMode enforcementMode = methodConfig.getScopesEnforcementMode();

        if (PolicyEnforcerConfig.ScopeEnforcementMode.ALL.equals(enforcementMode)) {
            return allowedScopes.containsAll(requiredScopes);
        }

        if (PolicyEnforcerConfig.ScopeEnforcementMode.ANY.equals(enforcementMode)) {
            for (String requiredScope : requiredScopes) {
                if (allowedScopes.contains(requiredScope)) {
                    return true;
                }
            }
        }

        return requiredScopes.isEmpty();
    }

    private MethodConfig getRequiredScopes(PathConfig pathConfig, ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        return pathConfig.getMethods().stream()
                .filter(i -> i.getMethod()
                        .equals(method.name()))
                .findFirst()
                .orElse(generateDefaultMethodConfig(pathConfig, request));
    }

    private MethodConfig generateDefaultMethodConfig(PathConfig pathConfig, ServerHttpRequest request) {
        MethodConfig methodConfig = new MethodConfig();

        methodConfig.setMethod(request.getMethod().name());
        List scopes = new ArrayList<>();

        if (Boolean.TRUE.equals(config.getHttpMethodAsScope())) {
            scopes.add(request.getMethod());
        } else {
            scopes.addAll(pathConfig.getScopes());
        }
        methodConfig.setScopes(scopes);
        methodConfig.setScopesEnforcementMode(PolicyEnforcerConfig.ScopeEnforcementMode.ANY);
        return methodConfig;
    }

    private Mono<String> getPermissionTicket(PathConfig pathConfig, MethodConfig methodConfig, WebAuthzClient webAuthzClient,
                                             ServerHttpRequest httpFacade) {
        if (config.getUserManagedAccess() != null) {
            WebProtectionResource protection = webAuthzClient.protection();
            WebPermissionResource permission = protection.permission();
            PermissionRequest permissionRequest = new PermissionRequest();

            permissionRequest.setResourceId(pathConfig.getId());
            permissionRequest.setScopes(new HashSet<>(methodConfig.getScopes()));

            return resolveClaims(pathConfig, httpFacade)
                    .flatMap(claims -> {
                        permissionRequest.setClaims(claims);
                        return permission.create(permissionRequest);
                    })
                    .map(resp -> resp.getTicket());
        }
        return Mono.empty();
    }


    protected Mono<Map<String, List<String>>> resolveClaims(PathConfig pathConfig, ServerHttpRequest request) {
        return Mono.zip(resolveClaims(config.getClaimInformationPointConfig(), request), resolveClaims(pathConfig.getClaimInformationPointConfig(), request),
                (c1, c2) -> {
                    c1.putAll(c2);
                    return c1;
                })
                .defaultIfEmpty(new HashMap<>());
    }

    private Mono<Map<String, List<String>>> resolveClaims(Map<String, Map<String, Object>> claimInformationPointConfig, ServerHttpRequest request) {
        if (claimInformationPointConfig != null) {
            for (Map.Entry<String, Map<String, Object>> claimDef : claimInformationPointConfig.entrySet()) {
                ReactiveClaimInformationPointProvider claimProvider = claimProviders.get(claimDef.getKey());
                if (claimProvider != null) {
                    return claimProvider.resolve(claimDef.getValue(), request);
                }
            }
        }
        return Mono.empty();
    }

   protected <T> Mono<T> handleAccessDenied(ServerHttpResponse response) {
        String accessDeniedPath = config.getOnDenyRedirectTo();
        HttpStatus status = null;
        if (accessDeniedPath != null) {
            response.getHeaders().set("Location", accessDeniedPath);
            status = HttpStatus.FOUND;
        } else {
            status = HttpStatus.FORBIDDEN;
        }
        return oauth2AuthenticationException(status.name());
    }

    private AuthorizationContext createAuthorizationContext(AccessToken accessToken, PathConfig pathConfig) {
        return new WebClientAuthorizationContext(accessToken, pathConfig, webAuthzClient);
    }

    private AuthorizationContext createEmptyAuthorizationContext(final boolean granted) {
        return new WebClientAuthorizationContext(webAuthzClient) {
            @Override
            public boolean hasPermission(String resourceName, String scopeName) {
                return granted;
            }

            @Override
            public boolean hasResourcePermission(String resourceName) {
                return granted;
            }

            @Override
            public boolean hasScopePermission(String scopeName) {
                return granted;
            }

            @Override
            public List<Permission> getPermissions() {
                return Collections.EMPTY_LIST;
            }

            @Override
            public boolean isGranted() {
                return granted;
            }
        };
    }
}
