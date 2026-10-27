package dev.lucaswotta.tickethit.ticketing.support;

import dev.lucaswotta.tickethit.ticketing.payment.PaymentClient;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Contexto único para todos os testes de integração: o Spring reaproveita o
 * mesmo contexto, e com ele os mesmos containers do PostgreSQL e do Kafka. O job
 * de expiração fica desligado para os testes decidirem quando expirar, e o
 * cliente do payment é um mock (o cliente tem teste próprio, PaymentClientTest).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "ticketing.hold.expiration-enabled=false")
@Import(TestInfrastructure.class)
@AutoConfigureMockMvc
@MockitoBean(types = PaymentClient.class)
public @interface IntegrationTest {}
