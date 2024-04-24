package com.creatism.keycloak.webflux;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyExtractors;
import reactor.core.publisher.Mono;

/**
 *
 * @author penough
 */
public class GenericBodyExtractor<T>
        implements BodyExtractor<Mono<T>, ReactiveHttpInputMessage> {
    private final ParameterizedTypeReference<T> genericType = new ParameterizedTypeReference<>() {
    };


    @Override
    public Mono<T> extract(ReactiveHttpInputMessage inputMessage, Context context) {
        BodyExtractor<Mono<T>, ReactiveHttpInputMessage> delegate = BodyExtractors
                .toMono(genericType);
        return delegate.extract(inputMessage, context)
                .onErrorMap((ex) -> new RuntimeException("An error occurred parsing the server configuration response: " + ex.getMessage()))
                .switchIfEmpty(Mono.error(() -> new RuntimeException("Empty Server Configuration Response")));
    }
}
