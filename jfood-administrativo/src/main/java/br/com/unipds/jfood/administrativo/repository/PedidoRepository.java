package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.domain.StatusPedido;
import br.com.unipds.jfood.administrativo.repository.projection.ResumoPedido;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // =========================================================================
    // Aula 6 — inteligencia no banco e paginacao
    // =========================================================================

    /**
     * Chama a funcao PL/pgSQL. Para funcao que devolve escalar, a query nativa e
     * o caminho mais direto -- para funcao que devolve conjunto de linhas, seria
     * StoredProcedureQuery via EntityManager.
     */
    @Query(value = "SELECT calcular_taxa_entrega(:pedidoId)", nativeQuery = true)
    BigDecimal calcularTaxaEntrega(@Param("pedidoId") Long pedidoId);

    /**
     * Paginacao por OFFSET.
     *
     * O custo cresce com o numero da pagina: para devolver 10 linhas da pagina
     * 5.000, o banco le e DESCARTA 50.000. Toda Page custa ainda uma segunda
     * consulta, o COUNT(*) que alimenta totalElements.
     *
     * A ordenacao e por (dataPedido, id), e o id nao e enfeite: sem um criterio
     * de desempate deterministico, dois pedidos do mesmo instante podem aparecer
     * na pagina 2 e na 3, e outro em nenhuma das duas.
     */
    @Query(value = """
                   SELECT p FROM Pedido p
                     JOIN FETCH p.restaurante
                     LEFT JOIN FETCH p.entregador
                    WHERE p.cliente.id = :clienteId
                    ORDER BY p.dataPedido DESC, p.id DESC
                   """,
           countQuery = "SELECT COUNT(p) FROM Pedido p WHERE p.cliente.id = :clienteId")
    Page<Pedido> listarHistoricoPaginado(@Param("clienteId") Long clienteId, Pageable pageable);

    /**
     * Paginacao por KEYSET (cursor).
     *
     * Em vez de pular linhas, ancora na ultima lida: o banco usa o indice e vai
     * DIRETO ao ponto. O custo e o mesmo na pagina 1 e na pagina 5.000.
     *
     * A comparacao de tupla -- (data_pedido, id) < (:ultimaData, :ultimoId) --
     * e o que torna a ancora correta quando ha empate na data. Comparar so a
     * data pularia ou repetiria pedidos do mesmo instante.
     *
     * A limitacao e real: nao da para "ir para a pagina 500". Se o produto exige
     * esse salto, offset e a escolha certa -- em uma base pequena.
     */
    @Query(value = """
                   SELECT p.* FROM pedido p
                    WHERE p.cliente_id = :clienteId
                      AND (p.data_pedido, p.id) < (:ultimaData, :ultimoId)
                    ORDER BY p.data_pedido DESC, p.id DESC
                    LIMIT :tamanho
                   """, nativeQuery = true)
    List<Pedido> listarHistoricoKeyset(@Param("clienteId") Long clienteId,
                                       @Param("ultimaData") OffsetDateTime ultimaData,
                                       @Param("ultimoId") Long ultimoId,
                                       @Param("tamanho") int tamanho);
}
