package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.Restaurante;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestauranteRepository extends JpaRepository<Restaurante, Long> {

    // -------------------------------------------------------------------------
    // Forma 3 — NATIVE QUERY: SQL do dialeto, quando a JPQL nao alcanca
    //
    // O caso que JUSTIFICA a query nativa e o recurso que so existe naquele banco.
    // Aqui e o full-text search do PostgreSQL, que resolve exatamente o que a
    // Etapa 2 mostrou nao ter solucao com indice B-tree: a busca por palavra no
    // MEIO do nome, o LIKE '%pizza%' que varria 200 mil linhas.
    //
    // NUNCA concatene a entrada do usuario:
    //   "... WHERE nome = '" + termoDigitado + "'"   <- porta aberta para injecao
    //
    // Com :termo, o Spring usa PreparedStatement e o PostgreSQL trata o conteudo
    // como DADO. Quem digitar "'; DROP TABLE restaurante; --" recebe zero
    // resultados, nao um banco destruido.
    // -------------------------------------------------------------------------

    @Query(value = """
                   SELECT * FROM restaurante r
                    WHERE to_tsvector('portuguese', r.nome)
                          @@ plainto_tsquery('portuguese', :termo)
                   """, nativeQuery = true)
    List<Restaurante> buscaTextualPorNome(@Param("termo") String termo);
}
