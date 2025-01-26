package com.polarbookshop.orderservice.book;

import com.polarbookshop.orderservice.config.ClientProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.URI;

/**
 * tests unitaires
 */
// pour éviter la dépendances entre les méthodes de tests - cas non rencontré sur les 2 TU implémentés
@TestMethodOrder(MethodOrderer.Random.class)
public class BookClientTests {
    private MockWebServer mockWebServer;
    private BookClient bookClient;
    //configuration serveur mock et du WebClient + démarrage du serveur pour chaque cas de test
    @BeforeEach
    void setUp() throws IOException {
        //config et démarrage du serveur web
        this.mockWebServer = new MockWebServer();
        this.mockWebServer.start();

        //configuration du WebClient
        var webClient = WebClient.builder()
                .baseUrl(this.mockWebServer.url("/").toString())
                .build();
        //configuration du bookClient avec le WebClient et ClientProperties
        this.bookClient = new BookClient(webClient, new ClientProperties(null,3,3,100));
    }

    //arrêt du serveur après chaque @Test
    @AfterEach
    void cleanUp() throws IOException {
        this.mockWebServer.shutdown();
    }

    @Test
    void whenBookExistsThenReturnBook() {
        var bookIsbn = "1234567890";
        //définition de la réponse http que le serveur mock doit retourner
        var mockResponse  = new MockResponse()
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                    {
                    "isbn": %s,
                    "title": "Title",
                    "author": "Author",
                    "price": 9.90,
                    "publisher": "Polarsophia"
                    }
                """.formatted(bookIsbn)); //formatage de la réponse par insertion de la valeur de bookIsbn
        //ajout de la réponse dans la queue du serveur mock
        mockWebServer.enqueue(mockResponse);

        Mono<Book> bookMono = bookClient.getBookByIsbn(bookIsbn); //définition d'un publisher de type Mono - aucun item n'est pour l'instant émis
        StepVerifier.create(bookMono) //sousscription au publisher Mono retourné par bookClient - le Mono émet son item
                .expectNextMatches(
                        book -> book.isbn().equals(bookIsbn)
                )
                .verifyComplete();
    }

    @Test
    void whenBookNotExistsThenReturnEmpty() {
        var bookIsbn = "1234567891";
        //définition d'une réponse http 404
        var mockResponse  = new MockResponse()
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setResponseCode(404);

        mockWebServer.enqueue(mockResponse);

        Mono<Book> bookMono = bookClient.getBookByIsbn(bookIsbn);
        // on attend un Mono vide retourné - ne contenant pas d'items
        StepVerifier.create(bookMono)
                .expectNextCount(0)
                .verifyComplete();
    }
}
