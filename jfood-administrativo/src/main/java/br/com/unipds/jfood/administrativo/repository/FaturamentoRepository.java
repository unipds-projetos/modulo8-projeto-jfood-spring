package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.Restaurante;
import br.com.unipds.jfood.administrativo.repository.projection.FaturamentoRestaurante;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Estende Restaurante so para ganhar um repositorio JPA; as consultas aqui sao
 * todas nativas, porque materialized view nao e entidade mapeada.
 */
public interface FaturamentoRepository extends JpaRepository<Restaurante, Long> {

    /** A leitura do snapshot: Seq Scan sobre meia duzia de linhas. */
    @Query(value = """
                   SELECT restaurante_id AS "restauranteId",
                          restaurante,
                          categoria,
                          pedidos_entregues AS "pedidosEntregues",
                          receita_total AS "receitaTotal",
                          ticket_medio AS "ticketMedio",
                          atualizado_em AS "atualizadoEm"
                     FROM mv_faturamento_por_restaurante
                    ORDER BY receita_total DESC
                   """, nativeQuery = true)
    List<FaturamentoRestaurante> lerSnapshot();
}
