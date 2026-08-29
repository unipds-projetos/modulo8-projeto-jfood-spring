package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.domain.StatusPedido;
import br.com.unipds.jfood.administrativo.repository.projection.ResumoPedido;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Derived query: o Spring le o nome do metodo e monta a consulta.
     * Traz SO os pedidos -- restaurante e entregador ficam para o lazy loading.
     */
    List<Pedido> findByClienteIdOrderByDataPedidoDesc(Long clienteId);

    /**
     * A mesma consulta, em UMA query.
     *
     * O left join fetch e obrigatorio no entregador: pedido ainda nao despachado
     * tem entregador_id nulo, e um inner join fetch faria esses pedidos sumirem
     * do historico -- um bug pior que o N+1, porque nao aparece no log.
     */
    @Query("""
           SELECT p FROM Pedido p
             JOIN FETCH p.restaurante
             LEFT JOIN FETCH p.entregador
            WHERE p.cliente.id = :clienteId
            ORDER BY p.dataPedido DESC
           """)
    List<Pedido> buscarHistoricoComRestauranteEEntregador(@Param("clienteId") Long clienteId);

    // -------------------------------------------------------------------------
    // Forma 1 — DERIVED QUERY: o nome do metodo e a consulta
    //
    // Elegante enquanto o filtro e simples. E o compilador nao protege: um
    // findByStattus, com dois tes, compila -- e quebra na subida da aplicacao.
    // -------------------------------------------------------------------------

    List<Pedido> findByStatusOrderByDataPedidoDesc(StatusPedido status);

    // -------------------------------------------------------------------------
    // Forma 2 — JPQL: consulta sobre CLASSES, nao sobre tabelas
    //
    // O ON de cada JOIN e deduzido do mapeamento: "JOIN p.restaurante r" ja sabe
    // que a condicao e r.id = p.restaurante_id. Voce navega o grafo de objetos.
    // -------------------------------------------------------------------------

    @Query("""
           SELECT p FROM Pedido p
             JOIN FETCH p.restaurante r
             JOIN r.categoria c
            WHERE p.cliente.id = :clienteId
              AND c.nome = :categoria
            ORDER BY p.dataPedido DESC
           """)
    List<Pedido> buscarPorClienteECategoria(@Param("clienteId") Long clienteId,
                                            @Param("categoria") String categoria);

    // -------------------------------------------------------------------------
    // Forma 4 — PROJECTION: trazer so o que se usa
    //
    // Os AS nao sao decoracao: o Spring casa cada alias com o nome do getter da
    // interface. Sem "AS numero", getNumero() volta null -- sem erro, sem log.
    // -------------------------------------------------------------------------

    @Query("""
           SELECT p.id AS numero,
                  r.nome AS restaurante,
                  p.valorTotal AS valorTotal
             FROM Pedido p
             JOIN p.restaurante r
            WHERE p.cliente.id = :clienteId
            ORDER BY p.dataPedido DESC
           """)
    List<ResumoPedido> listarResumoDoCliente(@Param("clienteId") Long clienteId);

    // =========================================================================
    // Aula 5 — concorrencia
    // =========================================================================

    /**
     * SELECT ... FOR UPDATE, expresso em JPA.
     *
     * O lock e adquirido NA LEITURA, antes de qualquer decisao -- e e isso que
     * fecha a janela onde o duplo pagamento nasce.
     *
     * O timeout nao e opcional. Sem ele, uma transacao travada bloqueia todas as
     * outras indefinidamente, e o sintoma que chega ao suporte e "o app parou".
     * Com 3 s, quem espera demais recebe um erro e a fila anda.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> buscarParaAtualizacao(@Param("id") Long id);

    /**
     * A fila de despacho.
     *
     * SKIP LOCKED PULA as linhas ja bloqueadas por outro processo em vez de
     * esperar. Cada instancia do servico pega o proprio lote, sem contencao e sem
     * deadlock -- e substitui um sistema de filas externo quando o banco ja e a
     * fonte da verdade.
     *
     * Aqui NAO se usa @Lock: o FOR UPDATE ja esta explicito no SQL nativo, e
     * anotar por cima so criaria conflito.
     */
    @Query(value = """
                   SELECT * FROM pedido
                    WHERE status = 'CONFIRMADO'
                      AND despachado_em IS NULL
                    ORDER BY data_pedido
                    LIMIT :limite
                      FOR UPDATE SKIP LOCKED
                   """, nativeQuery = true)
    List<Pedido> buscarLoteParaDespacho(@Param("limite") int limite);
}
