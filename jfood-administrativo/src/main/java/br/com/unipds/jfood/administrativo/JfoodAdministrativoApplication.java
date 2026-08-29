package br.com.unipds.jfood.administrativo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableResilientMethods      // liga os proxies do @Retryable (Aula 5)
public class JfoodAdministrativoApplication {

    public static void main(String[] args) {
        SpringApplication.run(JfoodAdministrativoApplication.class, args);
    }
}
