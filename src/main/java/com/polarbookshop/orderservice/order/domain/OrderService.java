package com.polarbookshop.orderservice.order.domain;

import com.polarbookshop.orderservice.book.Book;
import com.polarbookshop.orderservice.book.BookClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final BookClient bookClient;

    //injection par constructeur
    public OrderService(OrderRepository orderRepository, BookClient bookClient) {
        this.orderRepository = orderRepository;
        this.bookClient = bookClient;
    }
    public Flux<Order> getAllOrders() {
       //retourne le flux de sortie émettant les Order
        return orderRepository.findAll();
    }

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
                .flatMap(orderRepository::save);//opérateur sauvegardant la cammande et retournant un Mono<Order>
    }

    //Méthode utilitaire pour créer une commande rejetée. Note : dans le livre quantity est de type int
    public static Order buildRejectedOrder(String bookIsbn, Integer quantity) {
        return Order.of(bookIsbn,null, null, quantity, OrderStatus.REJECTED);
    }


    // Méthode utilitaire pour créer une commande acceptée
    public static Order buildAcceptedOrder(Book book , Integer quantity) {
        return Order.of(book.isbn(), book.title()+" - "+book.author(), book.price(), quantity, OrderStatus.ACCEPTED );
    }
}
