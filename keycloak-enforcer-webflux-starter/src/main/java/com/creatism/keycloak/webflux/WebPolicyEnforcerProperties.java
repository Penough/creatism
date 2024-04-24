package com.creatism.keycloak.webflux;

import lombok.Data;
import lombok.ToString;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
/**
 *
 * @author penough
 */
@Data
@ToString(callSuper = true)
@ConfigurationProperties(prefix = "spring.keycloak.web-policy-enforcer")
public class WebPolicyEnforcerProperties extends PolicyEnforcerConfig {
    private String clientRegistryId;
}
