package br.com.unipds.jfood.administrativo.web;

import br.com.unipds.jfood.administrativo.service.PedidoService;
import br.com.unipds.jfood.administrativo.web.dto.PedidoResumoResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
