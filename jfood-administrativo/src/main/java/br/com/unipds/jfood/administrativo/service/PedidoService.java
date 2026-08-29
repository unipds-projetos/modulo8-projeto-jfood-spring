package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.domain.StatusPedido;
import br.com.unipds.jfood.administrativo.repository.projection.ResumoPedido;
import br.com.unipds.jfood.administrativo.repository.PedidoRepository;
import br.com.unipds.jfood.administrativo.web.dto.PedidoResumoResponse;
import br.com.unipds.jfood.administrativo.web.dto.AvaliacaoResponse;
import br.com.unipds.jfood.administrativo.repository.AvaliacaoRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final AvaliacaoRepository avaliacaoRepository;

    public PedidoService(PedidoRepository pedidoRepository, AvaliacaoRepository avaliacaoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
    }

    /**
     * O N+1, de proposito.
     *
     * A consulta traz os pedidos em UMA query. Cada getRestaurante().getNome() e
     * cada getEntregador().getNome() dentro do laco dispara outra -- o proxy lazy
     * so vai ao banco quando alguem chama um getter dele.
     *
     * O numero medido esta em docs/aula03.md. A correcao vem em
     * listarPedidosDoClienteComFetch.
     */
    @Transactional(readOnly = true)
    public List<PedidoResumoResponse> listarPedidosDoCliente(Long clienteId) {
        List<Pedido> pedidos = pedidoRepository.findByClienteIdOrderByDataPedidoDesc(clienteId);

        List<PedidoResumoResponse> resposta = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            resposta.add(new PedidoResumoResponse(
                    pedido.getId(),
                    pedido.getRestaurante().getNome(),                       // +1 query
                    pedido.getEntregador() == null
                            ? null
                            : pedido.getEntregador().getNome(),              // +1 query
                    pedido.getStatus(),
                    pedido.getDataPedido(),
                    pedido.getValorTotal()));
        }
        return resposta;
    }

    /**
     * A mesma tela, corrigida: UMA query.
     *
     * O mapeamento continua LAZY -- trocar para EAGER resolveria aqui e criaria o
     * problema em toda outra consulta a Pedido. LAZY no mapeamento + JOIN FETCH na
     * consulta que precisa e a combinacao certa.
     */
    @Transactional(readOnly = true)
    public List<PedidoResumoResponse> listarPedidosDoClienteComFetch(Long clienteId) {
        return pedidoRepository.buscarHistoricoComRestauranteEEntregador(clienteId)
                .stream()
                .map(pedido -> new PedidoResumoResponse(
                        pedido.getId(),
                        pedido.getRestaurante().getNome(),
                        pedido.getEntregador() == null
                                ? null
                                : pedido.getEntregador().getNome(),
                        pedido.getStatus(),
                        pedido.getDataPedido(),
                        pedido.getValorTotal()))
                .toList();
    }

    /**
     * Remove um item do pedido tirando-o da COLECAO -- sem chamar delete() em
     * lugar nenhum.
     *
     * Rodado ANTES de declarar orphanRemoval, isto deixava a linha de item_pedido
     * no banco: remover da lista em memoria nao remove do banco, e o
     * CascadeType.REMOVE tambem nao ajuda -- ele age quando o PAI e deletado, e o
     * pai aqui esta vivo. Pior: o valor_total ja tinha sido recalculado, e o
     * pedido passava a valer menos do que a soma dos seus proprios itens.
     *
     * Com orphanRemoval = true declarado em Pedido.itens, o Hibernate emite o
     * DELETE FROM item_pedido WHERE id = ? ao fechar a transacao -- sem nenhuma
     * chamada explicita a delete(). Os dois numeros estao em docs/aula03.md.
     */
    /** Forma 1 — derived query. */
    @Transactional(readOnly = true)
    public List<PedidoResumoResponse> listarPorStatus(StatusPedido status) {
        return pedidoRepository.findByStatusOrderByDataPedidoDesc(status)
                .stream()
                .map(this::paraResumo)
                .toList();
    }

    /** Forma 2 — JPQL com JOIN pela categoria do restaurante. */
    @Transactional(readOnly = true)
    public List<PedidoResumoResponse> listarPorClienteECategoria(Long clienteId, String categoria) {
        return pedidoRepository.buscarPorClienteECategoria(clienteId, categoria)
                .stream()
                .map(this::paraResumo)
                .toList();
    }

    /** Forma 4 — projecao: tres colunas, sem instanciar a entidade. */
    @Transactional(readOnly = true)
    public List<ResumoPedido> listarResumoDoCliente(Long clienteId) {
        return pedidoRepository.listarResumoDoCliente(clienteId);
    }

    // =========================================================================
    // Aula 6 — paginacao e funcao no banco
    // =========================================================================

    /** Historico paginado por OFFSET. O .map preserva os metadados da Page. */
    @Transactional(readOnly = true)
    public Page<PedidoResumoResponse> listarHistoricoPaginado(Long clienteId, Pageable pageable) {
        return pedidoRepository.listarHistoricoPaginado(clienteId, pageable)
                .map(this::paraResumo);
    }

    /** Historico paginado por KEYSET. Sem COUNT, sem OFFSET, custo constante. */
    @Transactional(readOnly = true)
    public List<PedidoResumoResponse> listarHistoricoKeyset(Long clienteId,
                                                            OffsetDateTime ultimaData,
                                                            Long ultimoId,
                                                            int tamanho) {
        return pedidoRepository.listarHistoricoKeyset(clienteId, ultimaData, ultimoId, tamanho)
                .stream()
                .map(this::paraResumo)
                .toList();
    }

    /** Avaliacoes de um restaurante -- a outra tela que cresce sem limite. */
    @Transactional(readOnly = true)
    public Page<AvaliacaoResponse> listarAvaliacoes(Long restauranteId, Pageable pageable) {
        return avaliacaoRepository.listarPorRestaurante(restauranteId, pageable)
                .map(a -> new AvaliacaoResponse(
                        a.getId(),
                        a.getCliente().getNome(),
                        a.getNota(),
                        a.getComentario(),
                        a.getCriadoEm()));
    }

    /**
     * Chama a funcao PL/pgSQL da V14.
     *
     * O RAISE EXCEPTION ... USING ERRCODE do lado do banco so vale alguma coisa
     * se alguem LER o codigo aqui. Sem esta traducao, os dois casos de erro da
     * funcao viram o mesmo 500 generico -- e o cliente da API nao consegue
     * distinguir "pedido nao existe" de "pedido ja entregue".
     *
     * Ler o SQLSTATE e nao a mensagem tambem e deliberado: a mensagem muda com o
     * idioma do servidor e com o proximo refactor; o codigo, nao.
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularTaxaEntrega(Long pedidoId) {
        try {
            return pedidoRepository.calcularTaxaEntrega(pedidoId);
        } catch (DataAccessException e) {
            throw traduzirErroDaFuncao(e, pedidoId);
        }
    }

    private RuntimeException traduzirErroDaFuncao(DataAccessException e, Long pedidoId) {
        Throwable causa = e;
        while (causa != null && !(causa instanceof SQLException)) {
            causa = causa.getCause();
        }
        if (causa instanceof SQLException sql) {
            return switch (sql.getSQLState()) {
                case "P0002" -> new PedidoNaoEncontradoException(pedidoId);
                case "P0001" -> new TaxaNaoRecalculavelException(mensagemLimpa(sql));
                default -> e;
            };
        }
        return e;
    }

    /**
     * O PostgreSQL devolve a mensagem do RAISE junto com "ERROR: " e um bloco
     * "Where:" com a linha da funcao. Util no log, ruido na resposta da API.
     */
    private String mensagemLimpa(SQLException sql) {
        return sql.getMessage()
                .lines()
                .findFirst()
                .orElse(sql.getMessage())
                .replaceFirst("^ERROR:\\s*", "")
                .strip();
    }

    private PedidoResumoResponse paraResumo(Pedido pedido) {
        return new PedidoResumoResponse(
                pedido.getId(),
                pedido.getRestaurante().getNome(),
                pedido.getEntregador() == null ? null : pedido.getEntregador().getNome(),
                pedido.getStatus(),
                pedido.getDataPedido(),
                pedido.getValorTotal());
    }

    @Transactional
    public void removerItem(Long pedidoId, Long itemPedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));

        pedido.getItens().removeIf(item -> item.getId().equals(itemPedidoId));
        pedido.recalcularValorTotal();
        // dirty checking grava o novo valor_total ao fechar a transacao
    }
}
