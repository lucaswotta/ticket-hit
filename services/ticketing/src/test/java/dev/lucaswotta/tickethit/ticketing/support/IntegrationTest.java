package dev.lucaswotta.tickethit.ticketing.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Contexto único para todos os testes de integração: o Spring reaproveita o
 * mesmo contexto, e com ele o mesmo container do PostgreSQL. O job de expiração
 * fica desligado para os testes decidirem quando expirar.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "ticketing.hold.expiration-enabled=false")
@Import(TestInfrastructure.class)
@AutoConfigureMockMvc
public @interface IntegrationTest {}
