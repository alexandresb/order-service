package com.polarbookshop.orderservice.order.domain;

import com.polarbookshop.orderservice.book.Book;
import com.polarbookshop.orderservice.book.BookClient;
import com.polarbookshop.orderservice.event.OrderAcceptedMessage;
import com.polarbookshop.orderservice.event.OrderDispatchedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final BookClient bookClient;
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final StreamBridge streamBridge;

    //injection par constructeur
    public OrderService(OrderRepository orderRepository, BookClient bookClient, StreamBridge streamBridge) {
        this.orderRepository = orderRepository;
        this.bookClient = bookClient;
        this.streamBridge = streamBridge;
    }
    public Flux<Order> getAllOrders() {
       //retourne le flux de sortie émettant les Order
        return orderRepository.findAll();
    }

    @Transactional//exécution de la méthode dans le contexte d'une transaction englobant la persistance en base et la plublication du message
    public Mono<Order> submitOrder(String bookIsbn, Integer quantity) {
        /*Création d'un objet Mono (flux initial) émettant une seule commande.
        Application de l'opérateur flatMap sur ce flux Mono.
        La fonction en paramètre de flatMap extrait l'objet Order du flux Mono et le sauvegarde en base.
        Elle retourne un flux de sortie Mono "contenant" l'Order sauvegardé.
        */
//        return Mono.just(buildRejectedOrder(bookIsbn, quantity))
//            .flatMap(orderRepository::save);
        return bookClient
                .getBookByIsbn(bookIsbn)//flux Mono<Book> fourni par l'appel asynchrone à catalog-service
                .map(book-> buildAcceptedOrder(book, quantity)) //opérateur créant un flux Mono<Order> à partir de l'item Book émit par Mono<Book>
                .defaultIfEmpty(buildRejectedOrder(bookIsbn,quantity))//Si Mono<Order> est vide = pas de livre correspondant, création d'un Ordrer REJECTED
                .flatMap(orderRepository::save)//opérateur sauvegardant la cammande et retournant un Mono<Order>
                .doOnNext(this::publishOrderAcceptedMessage); //publication du message avec l'order persisté avec le statut ACCEPTED
    }

    //"consomme" le fux des OrderDispatchedMessage passé par la fonction consumeOrderDispatchedMessages
    public Flux<Order> updateOrdersWithDispatchedStatus(Flux<OrderDispatchedMessage> flux){
        return flux.
                flatMap(message ->
                        orderRepository.findById(message.orderId()))
                .map(this::buildDispatchedOrder)
                .flatMap(orderRepository::save);
    }

    //Méthode utilitaire pour créer une commande rejetée. Note : dans le livre quantity est de type int
    public static Order buildRejectedOrder(String bookIsbn, Integer quantity) {
        return Order.of(bookIsbn,null, null, quantity, OrderStatus.REJECTED);
    }


    // Méthode utilitaire pour créer une commande acceptée
    public static Order buildAcceptedOrder(Book book , Integer quantity) {
        return Order.of(book.isbn(), book.title()+" - "+book.author(), book.price(), quantity, OrderStatus.ACCEPTED );
    }

    //méthode utilitaire privée pour mettre à jour une commande existante avec le statut DISPATCHED
    private Order buildDispatchedOrder(Order existingOrder) {
        return new Order(existingOrder.id(),
                existingOrder.bookIsbn(),
                existingOrder.bookName(),
                existingOrder.bookPrice(),
                existingOrder.quantity(),
                OrderStatus.DISPATCHED,
                existingOrder.createdDate(),
                existingOrder.lastModifiedDate(),
                existingOrder.version());
    }

    private void publishOrderAcceptedMessage(Order order) {
        if(!order.status().equals(OrderStatus.ACCEPTED)) { return; }

        var orderAcceptedMessage = new OrderAcceptedMessage(order.id());
        log.info("Sending order accepted message with id {}", orderAcceptedMessage.orderId());
        var result = streamBridge.send("acceptOrder-out-0", orderAcceptedMessage); //création de du binding au démarrage de l'appli
        log.info("Result of sending order accepted message with id {} : {}", orderAcceptedMessage.orderId(), result);
    }
}
