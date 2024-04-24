package com.creatism.keycloak.webflux.adapter.authorization.cip;

/**
 *
 * @author penough
 */
public class NameValuePair {
    private final String name;
    private final String value;

    public NameValuePair(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}
