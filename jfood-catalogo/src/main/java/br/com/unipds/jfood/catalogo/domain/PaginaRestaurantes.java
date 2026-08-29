package br.com.unipds.jfood.catalogo.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * O que vai para o CACHE no lugar de um Page.
 *
 * Cachear PageImpl direto NAO funciona sem ajuda: ele nao tem construtor que o
 * Jackson consiga usar. O mixin da apostila resolve -- e resolve tambem no
 * Jackson 3 do Spring Boot 4, o que foi verificado. A tecnica correta e uma
 * INTERFACE anotada com @JsonDeserialize(as = CustomPageImpl.class), onde
 * CustomPageImpl e uma subclasse real de PageImpl com um @JsonCreator. E o que
 * o projeto javify-catalogo-spring usa.
 *
 * Aqui optamos pelo record proprio assim mesmo -- pelo argumento que a propria
 * apostila levanta na secao 9.7: nao se deveria cachear classes internas de
 * framework. Um PageImpl gravado no Redis hoje e uma incompatibilidade esperando
 * o proximo upgrade do Spring, com o agravante de que os dados ja estao la. E a
 * mesma razao do VIA_DTO, do lado do HTTP.
 */
public record PaginaRestaurantes(
        List<Restaurante> conteudo,
        int numeroDaPagina,
        int tamanhoDaPagina,
        long totalDeElementos,
        String ordenacao
) {

    public static PaginaRestaurantes de(Page<Restaurante> pagina) {
        return new PaginaRestaurantes(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getSort().toString());
    }

    public Page<Restaurante> paraPage() {
        Sort sort = "UNSORTED".equals(ordenacao) ? Sort.unsorted() : Sort.by(ordenacao.split(": ")[0]);
        return new PageImpl<>(conteudo,
                PageRequest.of(numeroDaPagina, tamanhoDaPagina, sort),
                totalDeElementos);
    }
}
