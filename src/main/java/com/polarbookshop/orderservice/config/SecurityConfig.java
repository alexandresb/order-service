package com.polarbookshop.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchange ->exchange
                                                .anyExchange().authenticated()//API accessible à tous les utilisateurs authentifiés
                )
                .oauth2ResourceServer(oauth2-> oauth2.jwt(Customizer.withDefaults()))//support de l'authentification JWT
                .requestCache(requestCacheSpec ->requestCacheSpec
                                        .requestCache(NoOpServerRequestCache.getInstance()))//pas de cache de session à maintenir car toutes les req doivent inclure l'access token
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}
