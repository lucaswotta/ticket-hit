package dev.lucaswotta.tickethit.queue.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Contexto único para os testes de integração, com Redis real. O job de
 * liberação fica desligado para cada teste decidir quando liberar.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        properties = {
            "queue.admission-enabled=false",
            "queue.pass.private-key=" + TestPassKeys.PRIVATE_KEY,
            "queue.pass.public-key=" + TestPassKeys.PUBLIC_KEY
        })
@Import(TestInfrastructure.class)
@AutoConfigureMockMvc
public @interface IntegrationTest {}
