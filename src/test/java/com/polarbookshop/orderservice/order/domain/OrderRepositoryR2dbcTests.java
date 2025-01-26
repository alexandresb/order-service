package com.polarbookshop.orderservice.order.domain;

import com.polarbookshop.orderservice.config.DataConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@DataR2dbcTest //active l'autoconfiguration pour ce qui relève des tests utilisant R2DBC & le scan des composants est limité au repo et aux entités
@Import(DataConfig.class)//pour l'activation de l'audit R2DBC
@Testcontainers //activation et nettoyage auto du container "postgres"
public class OrderRepositoryR2dbcTests {

    //déclaration du container pour les tests de la couche de données
    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16.3");

    @Autowired
    private OrderRepository orderRepository;

    //retourne l'URL de la base Postgres containerisée
    private static String r2dbcUrl(){
        return String.format("r2dbc:postgresql://%s:%s/%s",
                postgreSQLContainer.getHost(),
                postgreSQLContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                postgreSQLContainer.getDatabaseName());
    }


    //configuration de Flyway et de OrderRepository avec les valeurs fournies par la base Postgres du container de test
    //Redéfinition dynamique pour les tests des propriétés de configuration d'accès à Postgres définis dans application.yml
    @DynamicPropertySource //Annotation de méthode statique permettant d'ajouter dans l'Environment du contexte d'application de test des propriétés de configuration
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        //Ajout de la propriété de configuration utilisée par Flyway pour se connecter à la base du container de test
        registry.add("spring.flyway.url", postgreSQLContainer::getJdbcUrl);
        //Ajout des propriétés pour configurer l'accès R2DBC d'OrderRepository à la base du container de tests
        registry.add("spring.r2dbc.username", postgreSQLContainer::getUsername);
        registry.add("spring.r2dbc.password", postgreSQLContainer::getPassword);
        registry.add("spring.r2dbc.url", OrderRepositoryR2dbcTests::r2dbcUrl);
    }
    @Test
    void createRejectedOrder(){
        //création d'une commande rejetée via la méthode statique utilitaire d'OrderService
        var rejectedOrder = OrderService.buildRejectedOrder("1234567893",3);
        //vérification qu'orderRepository retourne bien une commande avec un statut rejeté
        StepVerifier.create(orderRepository.save(rejectedOrder))//create s'abonne au flux Mono retourné par orderRepositorySave
                .expectNextMatches( order -> order.status().equals(OrderStatus.REJECTED))
                .verifyComplete();
    }

    @Test
    void findNoOrderIfIdNotExist(){
        StepVerifier.create(orderRepository.findById(397L))
                .expectNextCount(0)
                .verifyComplete();
    }


}
