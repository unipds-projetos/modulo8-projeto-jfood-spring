package br.com.unipds.jfood.administrativo.web;

import br.com.unipds.jfood.administrativo.domain.StatusPedido;
import br.com.unipds.jfood.administrativo.repository.projection.ResumoPedido;
import br.com.unipds.jfood.administrativo.service.PedidoService;
import br.com.unipds.jfood.administrativo.web.dto.AvaliacaoResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import br.com.unipds.jfood.administrativo.web.dto.PedidoResumoResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    /** Historico do cliente -- a versao com N+1. Conte as queries no log. */
    @GetMapping("/clientes/{clienteId}/pedidos")
    public ResponseEntity<List<PedidoResumoResponse>> listarPedidosDoCliente(
            @PathVariable Long clienteId) {
        return ResponseEntity.ok(pedidoService.listarPedidosDoCliente(clienteId));
    }

    /** O mesmo historico com JOIN FETCH. Mesma resposta, uma query so. */
    @GetMapping("/clientes/{clienteId}/pedidos-otimizado")
    public ResponseEntity<List<PedidoResumoResponse>> listarPedidosDoClienteComFetch(
            @PathVariable Long clienteId) {
        return ResponseEntity.ok(pedidoService.listarPedidosDoClienteComFetch(clienteId));
    }

    /** Forma 1 — derived query: pedidos por status. */
    @GetMapping("/pedidos")
    public ResponseEntity<List<PedidoResumoResponse>> listarPorStatus(
            @RequestParam StatusPedido status) {
        return ResponseEntity.ok(pedidoService.listarPorStatus(status));
    }

    /** Forma 2 — JPQL: pedidos do cliente em restaurantes de uma categoria. */
    @GetMapping("/clientes/{clienteId}/pedidos/por-categoria")
    public ResponseEntity<List<PedidoResumoResponse>> listarPorCategoria(
            @PathVariable Long clienteId,
            @RequestParam String categoria) {
        return ResponseEntity.ok(pedidoService.listarPorClienteECategoria(clienteId, categoria));
    }

    /** Forma 4 — projecao: a tela de historico, com tres campos. */
    @GetMapping("/clientes/{clienteId}/pedidos/resumo")
    public ResponseEntity<List<ResumoPedido>> listarResumo(@PathVariable Long clienteId) {
        return ResponseEntity.ok(pedidoService.listarResumoDoCliente(clienteId));
    }

    // =========================================================================
    // Aula 6 — as duas telas que crescem sem limite
    // =========================================================================

    /**
     * Historico do cliente, paginado por OFFSET.
     *
     * Nenhum endpoint de listagem vai para producao sem Pageable. O findAll() sem
     * argumento e aceitavel em teste e em tela de configuracao com dez linhas --
     * em qualquer outro lugar e uma bomba-relogio com pavio proporcional ao
     * sucesso do produto.
     */
    @GetMapping("/clientes/{clienteId}/pedidos/pagina")
    public ResponseEntity<Page<PedidoResumoResponse>> listarHistoricoPaginado(
            @PathVariable Long clienteId,
            @PageableDefault(size = 20, sort = "dataPedido", direction = Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(pedidoService.listarHistoricoPaginado(clienteId, pageable));
    }

    /**
     * O mesmo historico, paginado por KEYSET.
     *
     * Na primeira chamada, passe uma data bem no futuro e um id alto -- e a
     * ancora inicial. Nas seguintes, passe a data e o id do ULTIMO item da
     * pagina anterior.
     */
    @GetMapping("/clientes/{clienteId}/pedidos/keyset")
    public ResponseEntity<java.util.List<PedidoResumoResponse>> listarHistoricoKeyset(
            @PathVariable Long clienteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ultimaData,
            @RequestParam Long ultimoId,
            @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok(
                pedidoService.listarHistoricoKeyset(clienteId, ultimaData, ultimoId, tamanho));
    }

    /** Avaliacoes de um restaurante, paginadas. */
    @GetMapping("/restaurantes/{restauranteId}/avaliacoes")
    public ResponseEntity<Page<AvaliacaoResponse>> listarAvaliacoes(
            @PathVariable Long restauranteId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pedidoService.listarAvaliacoes(restauranteId, pageable));
    }

    /** A taxa calculada pela funcao PL/pgSQL da V14. */
    @GetMapping("/pedidos/{pedidoId}/taxa-entrega")
    public ResponseEntity<Map<String, BigDecimal>> calcularTaxaEntrega(@PathVariable Long pedidoId) {
        return ResponseEntity.ok(Map.of("taxaEntrega", pedidoService.calcularTaxaEntrega(pedidoId)));
    }

    /** Remove um item do pedido. Confira no banco se a linha sumiu mesmo. */
    @DeleteMapping("/pedidos/{pedidoId}/itens/{itemPedidoId}")
    public ResponseEntity<Void> removerItem(@PathVariable Long pedidoId,
                                            @PathVariable Long itemPedidoId) {
        pedidoService.removerItem(pedidoId, itemPedidoId);
        return ResponseEntity.noContent().build();
    }
}
