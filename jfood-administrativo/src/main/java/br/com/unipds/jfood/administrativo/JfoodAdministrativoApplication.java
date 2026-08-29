package br.com.unipds.jfood.administrativo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableResilientMethods                                    // @Retryable (Aula 5)
@EnableScheduling                                          // refresh da materialized view (Aula 6)
// Serializar PageImpl direto expoe uma classe interna do Spring no JSON da API:
// se um campo interno mudar de nome, todo cliente quebra. VIA_DTO usa PagedModel.
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class JfoodAdministrativoApplication {

    public static void main(String[] args) {
        SpringApplication.run(JfoodAdministrativoApplication.class, args);
    }
}
