package com.creatism.keycloak.webflux.client;

import com.creatism.keycloak.webflux.WebPolicyEnforcerProperties;
import com.creatism.keycloak.webflux.client.exceptions.NullPathConfigException;
import com.creatism.keycloak.webflux.client.resources.WebProtectedResource;
import com.creatism.keycloak.webflux.client.util.WebPathMatcher;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.PathConfig;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Webflux path configuration matcher
 *
 * @author penough
 */
@Slf4j
public class WebPathConfigMatcher extends WebPathMatcher<PathConfig> {

    private final Map<String, PathConfig> paths;
    private final PathCache pathCache;
    private WebPolicyEnforcerProperties config;
    private WebAuthzClient authzClient;

    public WebPathConfigMatcher(WebPolicyEnforcerProperties config, WebAuthzClient authzClient) {
        this.config = config;
        PolicyEnforcerConfig.PathCacheConfig cacheConfig = config.getPathCacheConfig();

        if (cacheConfig == null) {
            cacheConfig = new PolicyEnforcerConfig.PathCacheConfig();
        }

        this.authzClient = authzClient;

        this.paths = configurePaths();
        this.pathCache = new PathCache(cacheConfig.getMaxEntries(), cacheConfig.getLifespan(), paths);


        log.debug("Initialization complete. Path configuration:");
        if(log.isDebugEnabled()) {
            for (PathConfig pathConfig : this.paths.values()) {
                log.debug(pathConfig.toString());
            }
        }
    }

    @Override
    public Mono<PathConfig> matchPath(String targetUri) {
        return hitCache(targetUri)
                .switchIfEmpty(loadPathsConfig(targetUri))
                .onErrorResume(NullPathConfigException.class, ex -> Mono.empty());
    }

    private Mono<PathConfig> hitCache(String targetUri) {
        PathCache.CacheEntry cache = pathCache.getCacheClone(targetUri);
        if (cache != null) {
            PathConfig pathConfig = cache.value();
            if(pathConfig != null) {
                return Mono.just(pathConfig);
            }
            // there are two conditions:
            // 1. just expired (return empty)
            // 2. just cached null (throws error)
            // so finally expose the CacheEntry and implement Cloneable for transactional operation
            return Mono.error(new NullPathConfigException("cached path config is null"));
        }
        return Mono.empty();
    }

    private Mono<PathConfig> loadPathsConfig(String targetUri) {
        PathConfig pathConfig = super.matches(targetUri);
        if(!needLoad(pathConfig)) {
            pathCache.put(targetUri, pathConfig);
            return pathConfig != null ? Mono.just(pathConfig) : Mono.empty();
        }
        return authzClient.protection().resource()
                .findByMatchingUri(targetUri)
                .flatMap(ls -> resolveResource(ls, pathConfig, targetUri));
    }

    private Boolean needLoad(PathConfig pathConfig) {
        return (config.getLazyLoadPaths() || config.getPathCacheConfig() != null) &&
                (pathConfig == null || pathConfig.isInvalidated() || pathConfig.getPath().contains("*"));
    }

    private Mono<PathConfig> resolveResource(List<ResourceRepresentation> matchingResources, PathConfig pathConfig, String targetUri) {
        if (matchingResources.isEmpty()) {
            // if this config is invalidated (e.g.: due to cache expiration) we remove and return null
            if (pathConfig != null && pathConfig.isInvalidated()) {
                paths.remove(targetUri);
                return Mono.empty();
            }
        } else {
            Map<String, Map<String, Object>> cipConfig = null;
            PolicyEnforcerConfig.EnforcementMode enforcementMode = PolicyEnforcerConfig.EnforcementMode.ENFORCING;
            ResourceRepresentation targetResource = matchingResources.get(0);
            List<PolicyEnforcerConfig.MethodConfig> methodConfig = null;
            boolean isStatic = false;

            if (pathConfig != null) {
                cipConfig = pathConfig.getClaimInformationPointConfig();
                enforcementMode = pathConfig.getEnforcementMode();
                methodConfig = pathConfig.getMethods();
                isStatic = pathConfig.isStatic();
            } else {
                for (PathConfig existingPath : paths.values()) {
                    if (targetResource.getId().equals(existingPath.getId())
                            && existingPath.isStatic()
                            && !PolicyEnforcerConfig.EnforcementMode.DISABLED.equals(existingPath.getEnforcementMode())) {
                        return Mono.empty();
                    }
                }
            }

            pathConfig = PathConfig.createPathConfigs(targetResource).iterator().next();

            if (cipConfig != null) {
                pathConfig.setClaimInformationPointConfig(cipConfig);
            }

            if (methodConfig != null) {
                pathConfig.setMethods(methodConfig);
            }

            pathConfig.setStatic(isStatic);
            pathConfig.setEnforcementMode(enforcementMode);
        }
        pathCache.put(targetUri, pathConfig);
        return Mono.just(pathConfig);
    }
    public void removeFromCache(String pathConfig) {
        pathCache.remove(pathConfig);
    }

    @Override
    protected String getPath(PathConfig entry) {
        return entry.getPath();
    }

    @Override
    protected Collection<PathConfig> getPaths() {
        return paths.values();
    }

    private Map<String, PathConfig> configurePaths() {
        WebProtectedResource protectedResource = this.authzClient.protection().resource();
        boolean loadPathsFromServer = !config.getLazyLoadPaths();

        for (PathConfig pathConfig : config.getPaths()) {
            if (!PolicyEnforcerConfig.EnforcementMode.DISABLED.equals(pathConfig.getEnforcementMode())) {
                loadPathsFromServer = false;
                break;
            }
        }

        if (loadPathsFromServer) {
            log.info("No path provided in configuration.");
            Map<String, PathConfig> paths = configureAllPathsForResourceServer(protectedResource).block();

            paths.putAll(configureDefinedPaths(protectedResource, config));

            return paths;
        } else {
            log.info("Paths provided in configuration.");
            return configureDefinedPaths(protectedResource, config);
        }
    }

    private Map<String, PathConfig> configureDefinedPaths(WebProtectedResource protectedResource, PolicyEnforcerConfig enforcerConfig) {
        Map<String, PathConfig> paths = Collections.synchronizedMap(new LinkedHashMap<String, PathConfig>());
        Flux.fromIterable(enforcerConfig.getPaths())
                .map(pathConfig -> {
                    ResourceRepresentation resource;
                    String resourceName = pathConfig.getName();
                    String path = pathConfig.getPath();
                    Mono<ResourceRepresentation> tmpMono;
                    if (resourceName != null) {
                        log.debug("Trying to find resource with name {} for path {}}.", resourceName, path);
                        tmpMono = protectedResource.findByName(resourceName);
                    } else {
                        log.debug("Trying to find resource with uri {} for path {}.", path, path);
                        tmpMono = protectedResource.findByUri(path)
                                .switchIfEmpty(protectedResource.findByMatchingUri(path))
                                .map(ls -> {
                                    if(ls.size() == 1) {
                                        return ls.get(0);
                                    }
                                    throw new RuntimeException("Multiple resources found with the same uri");
                                });
                    }
                    tmpMono.map(res -> {
                                pathConfig.setId(res.getId());
                                if (resourceName != null) {
                                    pathConfig.setStatic(true);
                                }
                                return res;
                            })
                            .block();
                    if (PolicyEnforcerConfig.EnforcementMode.DISABLED.equals(pathConfig.getEnforcementMode())) {
                        pathConfig.setStatic(true);
                    }

                    PathConfig existingPath = null;

                    for (PathConfig current : paths.values()) {
                        if (current.getPath().equals(pathConfig.getPath())) {
                            existingPath = current;
                            break;
                        }
                    }

                    if (existingPath == null) {
                        paths.put(pathConfig.getPath(), pathConfig);
                    } else {
                        existingPath.getMethods().addAll(pathConfig.getMethods());
                        existingPath.getScopes().addAll(pathConfig.getScopes());
                    }
                    return pathConfig;
                }).collect(Collectors.toList()).block();
        return paths;
    }

    private Mono<Map<String, PathConfig>> configureAllPathsForResourceServer(WebProtectedResource protectedResource) {
        log.info("Querying the server for all resources associated with this application.");


        if (!config.getLazyLoadPaths()) {
            return protectedResource.findAll()
                    .map(ls -> {
                        Map<String, PathConfig> paths = Collections.synchronizedMap(new HashMap<String, PathConfig>());
                        for (String id : ls) {
                            try {
                                protectedResource.findById(id)
                                        .map(des -> {
                                            for(PathConfig pathConfig : PathConfig.createPathConfigs(des)) {
                                                paths.put(pathConfig.getPath(), pathConfig);
                                            }
                                            return des;
                                        }).block();
                            } catch (URISyntaxException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return paths;
                    });
        }

        return Mono.just(Collections.synchronizedMap(new HashMap<String, PathConfig>()));
    }
}
