package com.creatism.keycloak.webflux;

import com.creatism.keycloak.webflux.adapter.authorization.cip.ReactiveClaimInformationPointProvider;
import com.creatism.keycloak.webflux.adapter.authorization.cip.WebHttpClaimInformationPointProvider;
import com.creatism.keycloak.webflux.adapter.authorization.util.WebPlaceHolders;
import com.creatism.keycloak.webflux.adapter.authorization.util.WebRequestPlaceHolderResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 *
 *
 * @author penough
 */
@Configuration
@Import({WebPolicyEnforcerProperties.class})
public class KeycloakWebPolicyEnforcerAutoConfiguration {


    @Bean
    public WebPlaceHolders placeHolders() {
        WebPlaceHolders webPlaceHolders =  new WebPlaceHolders();
        webPlaceHolders.putResolver(new WebRequestPlaceHolderResolver());
        return webPlaceHolders;
    }

    @Bean
    public Map<String, ReactiveClaimInformationPointProvider> claimProviders(WebPlaceHolders placeHolders) {
        Map<String, ReactiveClaimInformationPointProvider> claimProviders = new LinkedHashMap<>();
        ReactiveClaimInformationPointProvider httpClaimInformationPointProvider = new WebHttpClaimInformationPointProvider(placeHolders);
        claimProviders.put(httpClaimInformationPointProvider.getName(), httpClaimInformationPointProvider);
        return claimProviders;
    }

    @Bean
    public WebPolicyEnforcer webPolicyEnforcer(WebPolicyEnforcerProperties properties, ServerOAuth2AuthorizedClientRepository clientRepository,
                                               Map<String, ReactiveClaimInformationPointProvider> claimProviders) {
        return new WebPolicyEnforcer(properties, clientRepository, claimProviders);
    }

}
