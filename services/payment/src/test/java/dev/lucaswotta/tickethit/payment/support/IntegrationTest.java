package dev.lucaswotta.tickethit.payment.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Contexto único para os testes de integração, com PostgreSQL e Kafka reais.
 * Os jobs ficam desligados para cada teste decidir quando processar e publicar.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "payment.jobs-enabled=false")
@Import(TestInfrastructure.class)
@AutoConfigureMockMvc
public @interface IntegrationTest {}
