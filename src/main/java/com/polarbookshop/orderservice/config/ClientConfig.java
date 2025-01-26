package com.polarbookshop.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration //source de beans
public class ClientConfig {

    @Bean //spécifie une méthode configurant le bean Webclient qui sera enregistré dans le contexte Spring
    WebClient webClient(ClientProperties clientProperties, WebClient.Builder webClientBuilder) {
        return webClientBuilder.baseUrl(clientProperties.catalogServiceUri().toString()).build();
    }
}
