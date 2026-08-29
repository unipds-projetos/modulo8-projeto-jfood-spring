package br.com.unipds.jfood.administrativo.web;

import br.com.unipds.jfood.administrativo.domain.StatusPedido;
import br.com.unipds.jfood.administrativo.repository.projection.ResumoPedido;
import br.com.unipds.jfood.administrativo.service.PedidoService;
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

    /** Remove um item do pedido. Confira no banco se a linha sumiu mesmo. */
    @DeleteMapping("/pedidos/{pedidoId}/itens/{itemPedidoId}")
    public ResponseEntity<Void> removerItem(@PathVariable Long pedidoId,
                                            @PathVariable Long itemPedidoId) {
        pedidoService.removerItem(pedidoId, itemPedidoId);
        return ResponseEntity.noContent().build();
    }
}
