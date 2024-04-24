package com.creatism.keycloak.webflux.adapter.authorization.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 *
 * @author penough
 */
public class WebPlaceHolders {

    private Map<String, WebPlaceHolderResolver> resolvers;

    private static Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(.+?)\\}");
    private static Pattern PLACEHOLDER_PARAM_PATTERN = Pattern.compile("\\[(.+?)\\]");

    public WebPlaceHolders() {
    }

    public Map<String, WebPlaceHolderResolver> putResolver(WebPlaceHolderResolver resolver) {
        if(resolvers == null) {
            resolvers = new LinkedHashMap<>();
        }
        this.resolvers.put(resolver.getName(), resolver);
        return this.resolvers;
    }

    public List<String> resolve(String value, ServerHttpRequest httpFacade) {
        Map<String, List<String>> placeHolders = parsePlaceHolders(value, httpFacade);

        if (!placeHolders.isEmpty()) {
            value = formatPlaceHolder(value);

            for (Map.Entry<String, List<String>> entry : placeHolders.entrySet()) {
                List<String> values = entry.getValue();

                if (values.isEmpty() || values.size() > 1) {
                    return values;
                }

                value = value.replaceAll(entry.getKey(), values.get(0)).trim();
            }
        }

        return Arrays.asList(value);
    }

    static String getParameter(String source, String messageIfNotFound) {
        Matcher matcher = PLACEHOLDER_PARAM_PATTERN.matcher(source);

        while (matcher.find()) {
            return matcher.group(1).replaceAll("'", "");
        }

        if (messageIfNotFound != null) {
            throw new RuntimeException(messageIfNotFound);
        }

        return null;
    }

    private Map<String, List<String>> parsePlaceHolders(String value, ServerHttpRequest httpFacade) {
        Map<String, List<String>> placeHolders = Collections.emptyMap();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        boolean found = matcher.find();

        if (found) {
            placeHolders = new HashMap<>();
            do {
                String placeHolder = matcher.group(1);
                int resolverNameIdx = placeHolder.indexOf('.');

                if (resolverNameIdx == -1) {
                    throw new RuntimeException("Invalid placeholder [" + value + "]. Could not find resolver name.");
                }

                WebPlaceHolderResolver resolver = resolvers.get(placeHolder.substring(0, resolverNameIdx));

                if (resolver != null) {
                    List<String> resolved = resolver.resolve(placeHolder, httpFacade);

                    if (resolved != null) {
                        placeHolders.put(formatPlaceHolder(placeHolder), resolved);
                    }
                }
            } while (matcher.find());
        }

        return placeHolders;
    }

    private static String formatPlaceHolder(String placeHolder) {
        return placeHolder.replaceAll("\\{", "").replace("}", "").replace("[", "").replace("]", "").replace("[", "").replace("]", "");
    }
}
