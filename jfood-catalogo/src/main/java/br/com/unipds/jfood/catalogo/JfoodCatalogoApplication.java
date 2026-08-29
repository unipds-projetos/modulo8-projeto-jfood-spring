package br.com.unipds.jfood.catalogo;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching     // liga os proxies AOP do Spring Cache (Aula 9)
@EnableScheduling  // o job de write-behind dos contadores (Aula 9)
// Sem isto, o Jackson serializa PageImpl -- uma classe INTERNA do Spring -- e o
// JSON publico da API passa a depender dos campos privados dela. VIA_DTO usa
// PagedModel, que e contrato estavel.
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class JfoodCatalogoApplication {

    public static void main(String[] args) {
        SpringApplication.run(JfoodCatalogoApplication.class, args);
    }
}
