package com.creatism.keycloak.webflux.adapter.authorization.util;//package com.creatism.keycloak.webflux.adapter.authorization.util;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import org.keycloak.util.JsonSerialization;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//
//import java.util.Arrays;
//import java.util.List;
//
//public class WebKeycloakSecurityContextPlaceHolderResolver  implements WebPlaceHolderResolver {
//
//    @Override
//    public List<String> resolve(String placeHolder, ServerHttpRequest httpFacade) {
//        String source = placeHolder.substring(placeHolder.indexOf('.') + 1);
//        TokenPrincipal principal = request.getPrincipal();
//
//        if (source.endsWith("access_token")) {
//            return Arrays.asList(principal.getRawToken());
//        }
//
//        JsonNode jsonNode;
//
//        if (source.startsWith("access_token[")) {
//            jsonNode = JsonSerialization.mapper.valueToTree(principal.getToken());
//        } else {
//            throw new RuntimeException("Invalid placeholder [" + placeHolder + "]");
//        }
//
//        return JsonUtils.getValues(jsonNode, getParameter(source, "Invalid placeholder [" + placeHolder + "]"));
//    }
//
//    @Override
//    public String getName() {
//        return "keycloak";
//    }
//}
