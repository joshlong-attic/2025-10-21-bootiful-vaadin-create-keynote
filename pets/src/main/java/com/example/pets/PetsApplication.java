package com.example.pets;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.ImportHttpServices;

import javax.sql.DataSource;
import java.util.Collection;

@EnableResilientMethods
@ImportHttpServices(CatFactsClient.class)
@SpringBootApplication
@Import(PetsBeanRegistrar.class)
public class PetsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetsApplication.class, args);
    }

    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer() {
        return httpSecurity ->
                httpSecurity.webAuthn(a -> a
                        .rpName("vaadin")
                        .allowedOrigins("http://localhost:8080")
                        .rpId("localhost")
                );
    }
}

class PetsBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(@NonNull BeanRegistry registry, @NonNull Environment env) {
        registry.registerBean(JdbcUserDetailsManager.class,
                s -> s
                        .supplier(supplierContext -> new JdbcUserDetailsManager(supplierContext.bean(DataSource.class))));

    }
}


record CatFact(String fact) {
}

record CatFacts(Collection<CatFact> facts) {
}

interface CatFactsClient {

    @GetExchange("https://www.catfacts.net/api")
    CatFacts facts();
}

interface DogRepository extends ListCrudRepository<@NonNull Dog, @NonNull Long> {
}

record Dog(@Id int id, String name, String description, String owner) {
}

@Controller
@ResponseBody
class PetsController {

    private final DogRepository dogRepository;
    private final CatFactsClient catFactsClient;

    PetsController(DogRepository dogRepository, CatFactsClient catFactsClient) {
        this.dogRepository = dogRepository;
        this.catFactsClient = catFactsClient;
    }

    @Retryable(maxAttempts = 5)
    @ConcurrencyLimit(10)
    @GetExchange("/cats")
    CatFacts catFacts() {
        return this.catFactsClient.facts();
    }

    @GetMapping("/dogs")
    Collection<Dog> dogs() {
        return this.dogRepository.findAll();
    }

//    @GetMapping("/dogs")
}