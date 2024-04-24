package com.creatism.keycloak.webflux.client.resources;

import com.creatism.keycloak.webflux.client.WebConfiguration;
import com.creatism.keycloak.webflux.client.util.WebTokenCallable;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;
/**
 *
 * @author penough
 */
public class WebProtectedResource {

    private final WebClient http;

    private ServerConfiguration serverConfiguration;
    private final WebConfiguration configuration;
    private final WebTokenCallable pat;

    private final static ParameterizedTypeReference<List<ResourceRepresentation>> RESP_RESOURCE_LIST = new ParameterizedTypeReference<List<ResourceRepresentation>>() {
    };


    public WebProtectedResource(WebClient http, ServerConfiguration serverConfiguration, WebConfiguration configuration, WebTokenCallable pat) {
        this.http = http;
        this.serverConfiguration = serverConfiguration;
        this.configuration = configuration;
        this.pat = pat;
    }

    /**
     * Query the server for a resource given its <code>id</code>.
     *
     * @param id the resource id
     * @return a {@link ResourceRepresentation}
     */
    public Mono<ResourceRepresentation> findById(final String id) throws URISyntaxException {
        URI uri = new URI(serverConfiguration.getResourceRegistrationEndpoint() + "/" + URLEncoder.encode(id));
        return pat.call()
                .flatMap(token -> http.get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(ResourceRepresentation.class));
    }

    /**
     * Query the server for a resource given its <code>name</code> where the owner is the resource server itself.
     *
     * @param name the resource name
     * @return a {@link ResourceRepresentation}
     */
    public Mono<ResourceRepresentation> findByName(String name) {
        Mono<List<ResourceRepresentation>> mo = find(null, name, null, configuration.getResource(), null, null, false, true, true, null, null);
        // empty?
        return  mo.flatMap(ls -> {
            if(ls.size() > 0) {
                return Mono.just(ls.get(0));
            }
            return Mono.empty();
        });
    }

    /**
     * Query the server for a resource given its <code>name</code> and a given <code>ownerId</code>.
     *
     * @param name the resource name
     * @param ownerId the owner id
     * @return a {@link ResourceRepresentation}
     */
    public Mono<ResourceRepresentation> findByName(String name, String ownerId) {
        Mono<List<ResourceRepresentation>> mo = find(null, name, null, ownerId, null, null, false, true, true, null, null);
        return mo.map(ls -> ls.get(0));
    }

    public Mono<List<ResourceRepresentation>> findByUri(String uri) {
        return find(null, null, uri, null, null, null, false, false, true, null, null);
    }

    /**
     * Returns a list of resources that best matches the given {@code uri}. This method queries the server for resources whose
     *
     * @param uri the resource uri to match
     * @return a list of resources
     */
    public Mono<List<ResourceRepresentation>> findByMatchingUri(String uri) {
        return find(null, null, uri, null, null, null, true, false, true,null, null);
    }

    /**
     * Query the server for all resources.
     *
     * @return @return an array of strings with the resource ids
     */
    public Mono<String[]> findAll() {
        URI qryUri = buildUriWithQryParams(null,null , null, null, null, null, false, false, false, null, null);

        return pat.call()
                .flatMap(token -> http.get().uri(qryUri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(String[].class));
    }

    /**
     * Query the server for any resource with the matching arguments.
     *
     * @param id the resource id
     * @param name the resource name
     * @param uri the resource uri
     * @param owner the resource owner
     * @param type the resource type
     * @param scope the resource scope
     * @param matchingUri the resource uri. Use this parameter to lookup a resource that best match the given uri
     * @param exactName if the the {@code name} provided should have a exact match
     * @param deep if the result should be a list of resource representations with details about the resource. If false, only ids are returned
     * @param firstResult the position of the first resource to retrieve
     * @param maxResult the maximum number of resources to retrieve
     * @return a list of resource representations or an array of strings representing resource ids, depending on the generic type
     */
    public Mono<List<ResourceRepresentation>> find(final String id, final String name, final String uri, final String owner, final String type,
                            final String scope, final boolean matchingUri, final boolean exactName, final boolean deep,
                            final Integer firstResult, final Integer maxResult) {
        URI qryUri;
        if(deep) {
            qryUri = buildUriWithQryParams(id, name, uri, owner, type, scope, matchingUri, exactName, deep, firstResult, maxResult);
        } else {
            qryUri = buildUriWithQryParams(id, name, uri, owner, type, scope, matchingUri, false, false, firstResult, maxResult);
        }

        return pat.call()
                .flatMap(token -> http.get().uri(qryUri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(RESP_RESOURCE_LIST));
    }

    private URI buildUriWithQryParams(final String id, final String name, final String uri, final String owner, final String type, final String scope, final boolean matchingUri, final boolean exactName, final boolean deep, final Integer firstResult, final Integer maxResult) {
        return UriComponentsBuilder.fromUriString(serverConfiguration.getResourceRegistrationEndpoint())
                .queryParamIfPresent("_id", Optional.ofNullable(id))
                .queryParamIfPresent("name", Optional.ofNullable(name))
                .queryParamIfPresent("uri", Optional.ofNullable(uri))
                .queryParamIfPresent("owner", Optional.ofNullable(owner))
                .queryParamIfPresent("type", Optional.ofNullable(type))
                .queryParamIfPresent("scope", Optional.ofNullable(scope))
                .queryParamIfPresent("matchingUri", Optional.ofNullable(Boolean.valueOf(matchingUri).toString()))
                .queryParamIfPresent("exactName", Optional.ofNullable(Boolean.valueOf(exactName).toString()))
                .queryParamIfPresent("deep", Optional.ofNullable(Boolean.toString(deep)))
                .queryParamIfPresent("first", Optional.ofNullable(firstResult != null ? firstResult.toString() : null))
                .queryParamIfPresent("max", Optional.ofNullable(maxResult != null ? maxResult.toString() : Integer.toString(-1)))
                .build().toUri();
    }
}
