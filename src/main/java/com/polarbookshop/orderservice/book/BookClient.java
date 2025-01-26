package com.polarbookshop.orderservice.book;

import com.polarbookshop.orderservice.config.ClientProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
public class BookClient {
    private static final String BOOKS_ROOT_API = "/books/";
    private final WebClient webClient;
    private final ClientProperties clientProperties;

    public BookClient(WebClient webClient, ClientProperties clientProperties) {
        this.webClient = webClient;
        this.clientProperties = clientProperties;
    }

    //utilisation de l'API fluent fournie par WebClient
    public Mono<Book> getBookByIsbn(String bookIsbn) {
        return webClient
                .get()
                .uri(BOOKS_ROOT_API+bookIsbn)
                .retrieve()
                .bodyToMono(Book.class)
               // .timeout(Duration.ofSeconds(3),Mono.empty())
                .timeout(Duration.ofSeconds(clientProperties.timeout()),Mono.empty())
                .onErrorResume(WebClientResponseException.NotFound.class, exception->Mono.empty())
               // .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
                .retryWhen(Retry.backoff(clientProperties.maxRetry(), Duration.ofMillis(clientProperties.retryBackoff())))
                .onErrorResume(Exception.class, exception -> Mono.empty());
    }
}
