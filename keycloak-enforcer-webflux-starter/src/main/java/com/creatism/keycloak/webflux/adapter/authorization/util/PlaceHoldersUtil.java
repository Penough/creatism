package com.creatism.keycloak.webflux.adapter.authorization.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * provide static PlaceHolders functions in this package
 *
 * @author penough
 */
public class PlaceHoldersUtil {
    private static Pattern PLACEHOLDER_PARAM_PATTERN = Pattern.compile("\\[(.+?)\\]");

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

}
