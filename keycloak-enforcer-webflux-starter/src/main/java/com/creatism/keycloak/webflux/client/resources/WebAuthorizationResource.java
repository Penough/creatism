package com.creatism.keycloak.webflux.client.resources;

import com.creatism.keycloak.webflux.client.WebConfiguration;
import com.creatism.keycloak.webflux.client.util.WebTokenCallable;
import org.keycloak.OAuth2Constants;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;
import org.keycloak.representations.idm.authorization.Permission;
import org.keycloak.representations.idm.authorization.PermissionTicketToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
/**
 *
 * @author penough
 */
public class WebAuthorizationResource {

    private WebConfiguration configuration;
    private ServerConfiguration serverConfiguration;
    private WebClient http;
    private WebTokenCallable pat;

    public WebAuthorizationResource(WebConfiguration configuration, ServerConfiguration serverConfiguration, WebClient http, WebTokenCallable pat) {
        this.configuration = configuration;
        this.serverConfiguration = serverConfiguration;
        this.http = http;
        this.pat = pat;
    }

    public Mono<AuthorizationResponse> authorize(final AuthorizationRequest request) {
        return invoke(request)
                .flatMap(spec -> spec.bodyToMono(AuthorizationResponse.class));
    }

    private Mono<ResponseSpec> invoke(AuthorizationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Authorization request must not be null");
        }
        if (request.getAudience() == null) {
            request.setAudience(configuration.getResource());
        }
        if(pat == null) {
            Map<String, String> headers = new LinkedHashMap();
            MultiValueMap params = new LinkedMultiValueMap();
            configuration.getClientCredentialsProvider().setClientCredentials(configuration, headers, params);
            params.putAll(uma(request));
            return Mono.just(http.post().uri(serverConfiguration.getTokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(hs -> putMapIntoHttpHeaders(hs, headers))
                    .body(BodyInserters.fromFormData(params))
                    .retrieve());
        }
        return pat.call()
                .map(token -> http.post().uri(serverConfiguration.getTokenEndpoint())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer" + token)
                        .body(BodyInserters.fromFormData(uma(request)))
                        .retrieve());
    }

    private void putMapIntoHttpHeaders(HttpHeaders hs, Map<String, String> headers) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            hs.put(e.getKey(), Arrays.asList(e.getValue()));
        }
    }

    private MultiValueMap uma(AuthorizationRequest request) {
        String ticket = request.getTicket();
        PermissionTicketToken permissions = request.getPermissions();

        if (ticket == null && permissions == null) {
            throw new IllegalArgumentException("You must either provide a permission ticket or the permissions you want to request.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap();
        String grantType = OAuth2Constants.UMA_GRANT_TYPE;
        form.put(OAuth2Constants.GRANT_TYPE, Arrays.asList(grantType));
        putIfPresent(form, "ticket", ticket);
        putIfPresent(form, "claim_token", request.getClaimToken());
        putIfPresent(form, "claim_token_format", request.getClaimTokenFormat());
        putIfPresent(form, "pct", request.getPct());
        putIfPresent(form, "rpt", request.getRptToken());
        putIfPresent(form, "scope", request.getScope());
        putIfPresent(form, "audience", request.getAudience());
        putIfPresent(form, "subject_token", request.getSubjectToken());

        if (permissions != null) {
            for (Permission permission : permissions.getPermissions()) {
                String resourceId = permission.getResourceId();
                Set<String> scopes = permission.getScopes();
                StringBuilder value = new StringBuilder();

                if (resourceId != null) {
                    value.append(resourceId);
                }

                if (scopes != null && !scopes.isEmpty()) {
                    value.append("#");
                    for (String scope : scopes) {
                        if (!value.toString().endsWith("#")) {
                            value.append(",");
                        }
                        value.append(scope);
                    }
                }

                form.put("permission", Arrays.asList(value.toString()));
            }
        }

        AuthorizationRequest.Metadata metadata = request.getMetadata();

        if (metadata != null) {
            if (metadata.getIncludeResourceName() != null) {
                form.put("response_include_resource_name", Arrays.asList(metadata.getIncludeResourceName().toString()));
            }

            if (metadata.getLimit() != null) {
                form.put("response_permissions_limit", Arrays.asList(metadata.getLimit().toString()));
            }

            if (metadata.getResponseMode() != null) {
                form.put("response_mode", Arrays.asList(metadata.getResponseMode()));
            }

            if (metadata.getPermissionResourceFormat() != null) {
                form.put("permission_resource_format", Arrays.asList(metadata.getPermissionResourceFormat().toString()));
            }

            if (metadata.getPermissionResourceMatchingUri() != null) {
                form.put("permission_resource_matching_uri", Arrays.asList(metadata.getPermissionResourceMatchingUri().toString()));
            }
        }

        return form;
    }

    private void putIfPresent(MultiValueMap form, String key, String value) {
        if(!StringUtils.hasText(value)) {
            return;
        }
        form.put(key, Arrays.asList(value));
    }
}
