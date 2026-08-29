package br.com.unipds.jfood.catalogo.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * O que vai para o CACHE no lugar de um Page.
 *
 * Cachear PageImpl direto nao funciona -- e a razao esta em docs/aula09.md:
 * ele nao tem construtor que o Jackson consiga usar, e o Pageable que os
 * construtores reais pedem tambem nao e desserializavel. O mixin da apostila
 * resolve isso no Jackson 2; no Jackson 3 do Spring Boot 4, nao.
 *
 * A saida e melhor do que o remendo: guardar um record proprio. Ele e estavel,
 * serializa e desserializa sem truque, e nao acopla o conteudo do cache a uma
 * classe interna do Spring -- que e exatamente a fragilidade contra a qual o
 * VIA_DTO existe do lado do HTTP.
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
