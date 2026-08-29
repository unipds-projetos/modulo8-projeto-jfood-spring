package br.com.unipds.jfood.catalogo;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
// Sem isto, o Jackson serializa PageImpl -- uma classe INTERNA do Spring -- e o
// JSON publico da API passa a depender dos campos privados dela. VIA_DTO usa
// PagedModel, que e contrato estavel.
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class JfoodCatalogoApplication {

    public static void main(String[] args) {
        SpringApplication.run(JfoodCatalogoApplication.class, args);
    }
}
