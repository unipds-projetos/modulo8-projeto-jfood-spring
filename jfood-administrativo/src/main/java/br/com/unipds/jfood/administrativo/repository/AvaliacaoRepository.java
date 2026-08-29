package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.Avaliacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    /**
     * Uma das duas telas do JFood que crescem sem limite.
     *
     * O JOIN FETCH do cliente evita o N+1 dentro da pagina; a ordenacao explicita
     * e por (criadoEm, id) porque so criadoEm nao e deterministico -- duas
     * avaliacoes no mesmo instante podem trocar de lugar entre a pagina 2 e a 3,
     * e o sintoma e uma lista que "as vezes repete um comentario".
     */
    @Query(value = """
                   SELECT a FROM Avaliacao a
                     JOIN FETCH a.cliente c
                    WHERE a.restaurante.id = :restauranteId
                    ORDER BY a.criadoEm DESC, a.id DESC
                   """,
           countQuery = "SELECT COUNT(a) FROM Avaliacao a WHERE a.restaurante.id = :restauranteId")
    Page<Avaliacao> listarPorRestaurante(@Param("restauranteId") Long restauranteId, Pageable pageable);
}
