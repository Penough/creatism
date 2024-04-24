# keycloak-enforcer-webflux-starter

## START
import the dependencies:
```xml
<dependency>
    <groupId>com.creatism</groupId>
    <artifactId>keycloak-enforcer-webflux-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<!--  -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```
add the WebFilter to security chain like this:
```java
http.addFilterAfter(new PolicyEnforcerWebFilter(enforcer), SecurityWebFiltersOrder.SECURITY_CONTEXT_SERVER_WEB_EXCHANGE);
```
## yaml
yaml configuration file format is the same as the <a href="https://www.keycloak.org/docs/latest/authorization_services/index.html#_enforcer_overview">keycloak official introduce</a>.<br>
and you can config it in yaml like this:
```yaml
spring:
  keycloak:
    web-policy-enforcer:
      client-registry-id: keycloak
      auth-server-url: http://localhost:8180
      realm: myrealm
      enforcement-mode: ENFORCING
      resource: my-client
      lazy-load-paths: true
      credentials:
        secret: ${your-client-secret}
      paths:
        - name: test
          type: get
          methods:
            - method: POST
              scopes:
                - "urn:123:ruei:r"

```
