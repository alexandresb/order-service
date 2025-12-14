package com.polarbookshop.orderservice.order.web;

import com.polarbookshop.orderservice.order.domain.Order;
import com.polarbookshop.orderservice.order.domain.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController//stéréotype spécifiant un bean implémentant des méthodes gérant / exposant les points de terminaison REST
@RequestMapping("orders")
public class OrderController {

    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    //injection  d'une représentation du principal correspondant à l'utilisateur authentifié
    @GetMapping
    public Flux<Order> getAllOrders(@AuthenticationPrincipal Jwt jwt){//injection d'une représentation du principal correspondant à l'utilisateur authentifié
        LOG.info("Fetching all orders");
       // retourne le flux retourné par order Service qui va émettre les commandes
        return orderService.getAllOrders(jwt.getSubject());
    }

    @PostMapping
    public Mono<Order> submitOrder(@RequestBody @Valid OrderRequest orderRequest) {
        LOG.info("Order for {} copies of the book with ISBN {}", orderRequest.quantity(), orderRequest.isbn());
        return orderService.submitOrder(orderRequest.isbn(),orderRequest.quantity());
    }
}
