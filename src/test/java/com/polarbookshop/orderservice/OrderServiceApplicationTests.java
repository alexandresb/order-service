package com.polarbookshop.orderservice;

import com.polarbookshop.orderservice.book.Book;
import com.polarbookshop.orderservice.book.BookClient;
import com.polarbookshop.orderservice.order.domain.Order;
import com.polarbookshop.orderservice.order.domain.OrderStatus;
import com.polarbookshop.orderservice.order.web.OrderRequest;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderServiceApplicationTests {

	@Container
	static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16.3");

	@Autowired
	private WebTestClient webClient;

	@MockBean
	BookClient bookClient;


	//retourne l'URL de la base Postgres containerisée
	private static String r2dbcUrl(){
		return String.format("r2dbc:postgresql://%s:%s/%s",
				postgreSQLContainer.getHost(),
				postgreSQLContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
				postgreSQLContainer.getDatabaseName());
	}


	//configuration de Flyway et de OrderRepository avec les valeurs fournies par la base Postgres du container de test
	//Redéfinition dynamique pour les tests des propriétés de configuration d'accès à Postgres définis dans application.yml
	@DynamicPropertySource
	//Annotation de méthode statique permettant d'ajouter dans l'Environment du contexte d'application de test des propriétés de configuration
	static void postgresqlProperties(DynamicPropertyRegistry registry) {
		//Ajout de la propriété de configuration utilisée par Flyway pour se connecter à la base du container de test
		registry.add("spring.flyway.url", postgreSQLContainer::getJdbcUrl);
		//Ajout des propriétés pour configurer l'accès R2DBC d'OrderRepository à la base du container de tests
		registry.add("spring.r2dbc.username", postgreSQLContainer::getUsername);
		registry.add("spring.r2dbc.password", postgreSQLContainer::getPassword);
		registry.add("spring.r2dbc.url", OrderServiceApplicationTests::r2dbcUrl);
	}

	@Test
	void contextLoads() {
	}

	@Test
	void whenGetOrdersThenReturn() {
		String bookIsbn = "1234567893";
		Book returnedBook = new Book(bookIsbn, "Title", "Author", 9.90);
		//définition du comportement du mock
		BDDMockito.given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(returnedBook));

		OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);
		//requête post vers le service Order pour créer une commande
		Order expectedOrder = webClient.post()
				.uri("/orders")
				.bodyValue(orderRequest)
				.exchange()
				.expectBody(Order.class).returnResult().getResponseBody();
		//vérification que le corps de la réponse contient bien une commande
		assertThat(expectedOrder).isNotNull();

		//vérifie qu'au moins une commande pour le livre d'ISBN donné est retournée
		webClient.get()
				.uri("/orders")
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBodyList(Order.class).value(
						orders -> {
							assertThat(orders.stream().filter(order
									-> order.bookIsbn().equals(bookIsbn)).findAny())
									.isNotEmpty();
						});
	}

	@Test
	void whenPostRequestAndBookExistsThenOrderAccepted(){
		String bookIsbn = "1234567899";
		Book returnedBook = new Book(bookIsbn, "Title", "Author", 9.90);
		//définition du comportement du mock
		BDDMockito.given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(returnedBook));

		OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);

		//requête post vers le service Order pour créer une commande acceptée
		Order createdOrder = webClient.post()
				.uri("/orders")
				.bodyValue(orderRequest)
				.exchange()
				.expectBody(Order.class).returnResult().getResponseBody();
		//vérification de la commande créée
		assertThat(createdOrder).isNotNull();
		assertThat(createdOrder.bookIsbn()).isEqualTo(orderRequest.isbn());
		assertThat(createdOrder.quantity()).isEqualTo(orderRequest.quantity());
		assertThat(createdOrder.bookName()).isEqualTo(returnedBook.title()+" - "+returnedBook.author());
		assertThat(createdOrder.bookPrice()).isEqualTo(returnedBook.price());
		assertThat(createdOrder.status()).isEqualTo(OrderStatus.ACCEPTED);
	}

	@Test
	void whenPostRequestAndBookNotExistsThenOrderRejected(){
		String bookIsbn = "1234567894";
		BDDMockito.given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.empty());

		OrderRequest orderRequest = new OrderRequest(bookIsbn, 3);

		//requête post vers le service Order pour créer une commande rejetée
		Order createdOrder = webClient.post()
				.uri("/orders")
				.bodyValue(orderRequest)
				.exchange()
				.expectBody(Order.class).returnResult().getResponseBody();
		//vérification de la commande créée
		assertThat(createdOrder).isNotNull();
		assertThat(createdOrder.bookIsbn()).isEqualTo(orderRequest.isbn());
		assertThat(createdOrder.quantity()).isEqualTo(orderRequest.quantity());
		assertThat(createdOrder.status()).isEqualTo(OrderStatus.REJECTED);
	}
}
