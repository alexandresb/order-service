package com.polarbookshop.orderservice.order.web;

import com.polarbookshop.orderservice.order.domain.Order;
import com.polarbookshop.orderservice.order.domain.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController//stéréotype spécifiant un bean implémentant des méthodes gérant / exposant les points de terminaison REST
@RequestMapping("orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    //injection  d'une représentation du principal correspondant à l'utilisateur authentifié
    @GetMapping
    public Flux<Order> getAllOrders(@AuthenticationPrincipal Jwt jwt){//injection d'une représentation du principal correspondant à l'utilisateur authentifié

       // retourne le flux retourné par order Service qui va émettre les commandes
        return orderService.getAllOrders(jwt.getSubject());
    }

    @PostMapping
    public Mono<Order> submitOrder(@RequestBody @Valid OrderRequest orderRequest) {
        return orderService.submitOrder(orderRequest.isbn(),orderRequest.quantity());
    }
}
