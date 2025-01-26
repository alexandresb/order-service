package com.polarbookshop.orderservice.event;

import com.polarbookshop.orderservice.order.domain.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

@Configuration
public class OrderFunctions {
    private static final Logger log = LoggerFactory.getLogger(OrderFunctions.class);

    //inscrit la fonction Consumer retournée en tant que bean -entrée = flux de messages - orderService injecté
    @Bean
    public Consumer<Flux<OrderDispatchedMessage>> consumeOrderDispatchedMessages(OrderService orderService) {
        return flux-> //le flux reçu par la fonction
                orderService.updateOrdersWithDispatchedStatus(flux)// flux passé pour traitement à OrderService
                        //pour chaque item Order (traité / mis à jour) du flux
                        .doOnNext(order-> log.info("the order with id {} is updated",order.id()))
                        //Nécessaire pour que le flux de message /entrant réceptionné soit activé
                        // = nécessaire pour que le flux commence à émettre des item
                        .subscribe();

    }
}
