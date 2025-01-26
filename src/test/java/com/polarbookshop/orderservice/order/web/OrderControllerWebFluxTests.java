package com.polarbookshop.orderservice.order.web;

import com.polarbookshop.orderservice.order.domain.Order;
import com.polarbookshop.orderservice.order.domain.OrderService;
import com.polarbookshop.orderservice.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(controllers = OrderController.class) //spécifie une classe de Test faisant le focus sur les composants Spring WebFlux
public class OrderControllerWebFluxTests {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private OrderService orderService;

    @Test
    void whenBookNotAvailableThenRejectOrder(){
        //définition de la commande attendue
        var orderRequest = new OrderRequest("1234567890",3);
        var expectedOrder = OrderService.buildRejectedOrder(orderRequest.isbn(),orderRequest.quantity());
       //définition du comportement du mock d'OrderService lors de la soumission d'une requête de commande
        BDDMockito.given(
             orderService.submitOrder(orderRequest.isbn(),orderRequest.quantity())
        ).willReturn(Mono.just(expectedOrder));

        webClient.post()
                .uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Order.class).value(actualOrder -> {
                    assertThat(actualOrder).isNotNull();
                    assertThat(actualOrder.status()).isEqualTo(OrderStatus.REJECTED);
                });
    }
}
