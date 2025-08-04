package com.polarbookshop.orderservice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polarbookshop.orderservice.book.Book;
import com.polarbookshop.orderservice.book.BookClient;
import com.polarbookshop.orderservice.event.OrderAcceptedMessage;
import com.polarbookshop.orderservice.order.domain.Order;
import com.polarbookshop.orderservice.order.domain.OrderStatus;
import com.polarbookshop.orderservice.order.web.OrderRequest;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnableTestBinder
@AutoConfigureWebTestClient(timeout = "36000") // pour éviter les timeout aléatoires durant les tests
class OrderServiceApplicationTests {

	@Container
	static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16.3");

	//définition du container Keycloak
	@Container
	private static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.3")
			.withRealmImportFile("test-realm-config.json");


	@Autowired
	private WebTestClient webClient;

	@Autowired
	private OutputDestination outputDestination; // abstraction de la destination de sortie dans lequel les events Accepted Order sont publiés

	@Autowired
	private ObjectMapper objectMapper;

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

		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
				() -> keycloakContainer.getAuthServerUrl()+"/realms/PolarBookshop");
	}

	//définition d'un record contenant l'access token
	// annoté pour indiquer àJackson comment obtenir /désérialiser l'access token depuis la réponse JSON
	private record KeycloakToken(String accessToken) {
		@JsonCreator
		private KeycloakToken(@JsonProperty("access_token") final String accessToken) {
			this.accessToken = accessToken;
		}
	}

	//méthode privée pour obtenir depuis le client web les tokens de tests via password grant flow
	private static KeycloakToken authenticateWith(String username, String password, WebClient webClient) {
		return webClient
				.post()
				.body(BodyInserters.fromFormData("grant_type", "password")
						.with("client_id","polar-test")
						.with("username", username)
						.with("password", password)
				)
				.retrieve()
				.bodyToMono(KeycloakToken.class)
				.block();//on bloque le client reactif jusqu'à obtention du token -> mode impératif
	}

	private static KeycloakToken bjornTokens;
	private static KeycloakToken isabelleTokens;

	//génération d'access token avant l'exécution des tests
	@BeforeAll
	static void generateAccessTokens() {
		//construction d'une requête vers le EP Keycloak d'obtention d'un access token un client web (pas de test)
		WebClient webClient =WebClient.builder()
				.baseUrl(keycloakContainer.getAuthServerUrl()+"/realms/PolarBookshop/protocol/openid-connect/token")//adresse d'obtention d'un token
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
				.build();

		// envoi des requêtes pour générer les access tokens
		isabelleTokens = authenticateWith("isabelle", "password", webClient);
		bjornTokens = authenticateWith("bjorn", "password", webClient);
	}

	@Test
	void contextLoads() {
	}

	@Test
	void whenGetOrdersThenReturn() throws IOException {
		String bookIsbn = "1234567893";
		Book returnedBook = new Book(bookIsbn, "Title", "Author", 9.90);
		//définition du comportement du mock
		BDDMockito.given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(returnedBook));

		OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);
		//requête post vers le service Order pour créer une commande
		Order expectedOrder = webClient.post()
				.uri("/orders")
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.bodyValue(orderRequest)
				.exchange()
				.expectBody(Order.class).returnResult().getResponseBody();
		//vérification que le corps de la réponse contient bien une commande
		assertThat(expectedOrder).isNotNull();

		//récupération et vérification du message publié
		assertThat(objectMapper.readValue(outputDestination.receive().getPayload(), OrderAcceptedMessage.class))
				.isEqualTo(new OrderAcceptedMessage(expectedOrder.id()));

		//vérifie qu'au moins une commande est retournée - maj chap12
		webClient.get()
				.uri("/orders")
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBodyList(Order.class).value(//maj dans chap12
						orders -> {
							List<Long> ordersId = orders.stream()
											.map(Order::id)
											.collect(Collectors.toList());
							assertThat(ordersId).contains(expectedOrder.id());
						});
	}

	@Test
	void whenPostRequestAndBookExistsThenOrderAccepted() throws IOException {
		String bookIsbn = "1234567899";
		Book returnedBook = new Book(bookIsbn, "Title", "Author", 9.90);
		//définition du comportement du mock
		BDDMockito.given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(returnedBook));

		OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);

		//requête post vers le service Order pour créer une commande acceptée
		Order createdOrder = webClient.post()
				.uri("/orders")
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.bodyValue(orderRequest)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();
		//vérification de la commande créée
		assertThat(createdOrder).isNotNull();
		assertThat(createdOrder.bookIsbn()).isEqualTo(orderRequest.isbn());
		assertThat(createdOrder.quantity()).isEqualTo(orderRequest.quantity());
		assertThat(createdOrder.bookName()).isEqualTo(returnedBook.title()+" - "+returnedBook.author());
		assertThat(createdOrder.bookPrice()).isEqualTo(returnedBook.price());
		assertThat(createdOrder.status()).isEqualTo(OrderStatus.ACCEPTED);


		//vérification que le message envoyé est bien celui correspondant à l'order créé par requête post
		assertThat(objectMapper.readValue(outputDestination.receive().getPayload(), OrderAcceptedMessage.class))
				.isEqualTo(new OrderAcceptedMessage(createdOrder.id()));
		System.out.println("contenu message" +objectMapper.writeValueAsString(createdOrder));
		System.out.println("order id "+ createdOrder.id());
	}

	@Test
	void whenPostRequestAndBookNotExistsThenOrderRejected(){
		String bookIsbn = "1234567894";
		BDDMockito.given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.empty());

		OrderRequest orderRequest = new OrderRequest(bookIsbn, 3);

		//requête post vers le service Order pour créer une commande rejetée
		Order createdOrder = webClient.post()
				.uri("/orders")
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.bodyValue(orderRequest)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();
		//vérification de la commande créée
		assertThat(createdOrder).isNotNull();
		assertThat(createdOrder.bookIsbn()).isEqualTo(orderRequest.isbn());
		assertThat(createdOrder.quantity()).isEqualTo(orderRequest.quantity());
		assertThat(createdOrder.status()).isEqualTo(OrderStatus.REJECTED);
	}

	//ajout fin chap12 pour tester que les commandes retournées sont bien associées à l'utilisateur les ayant créées
	@Test
	void whenGetOrdersForAnotherUserThenNotReturned() throws IOException {
		String bookIsbn = "1234567899";
		Book returnedBook = new Book(bookIsbn, "Title", "Author", 9.90);
		//définition du comportement du mock
		BDDMockito.given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(returnedBook));

		OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);

		//requête post vers le service Order pour que bjorn crée une commande
		Order bjornOrder = webClient.post()
				.uri("/orders")
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.bodyValue(orderRequest)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();

		assertThat(bjornOrder).isNotNull();
		assertThat(objectMapper.readValue(outputDestination.receive().getPayload(), OrderAcceptedMessage.class))
				.isEqualTo(new OrderAcceptedMessage(bjornOrder.id()));

		//requête post vers le service Order pour qu'Isabelle crée une commande
		Order isabelleOrder = webClient.post()
				.uri("/orders")
				.headers(headers -> headers.setBearerAuth(isabelleTokens.accessToken()))
				.bodyValue(orderRequest)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();

		assertThat(isabelleOrder).isNotNull();
		assertThat(objectMapper.readValue(outputDestination.receive().getPayload(), OrderAcceptedMessage.class))
				.isEqualTo(new OrderAcceptedMessage(isabelleOrder.id()));

		//tests que la liste des commandes demandée par Bjorn ne contient que les commandes de Bjorn
		webClient.get()
				.uri("/orders")
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBodyList(Order.class)
				.value(//maj dans chap12
						orders -> {
							List<Long> ordersId = orders.stream()
									.map(Order::id)
									.collect(Collectors.toList());
							assertThat(ordersId).contains(bjornOrder.id());
							assertThat(ordersId).doesNotContain(isabelleOrder.id());
						});

	}
}
