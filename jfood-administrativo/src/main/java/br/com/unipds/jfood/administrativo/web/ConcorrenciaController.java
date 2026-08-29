package br.com.unipds.jfood.administrativo.web;

import br.com.unipds.jfood.administrativo.service.AutoInvocacaoService;
import br.com.unipds.jfood.administrativo.service.CancelamentoPedidoService;
import br.com.unipds.jfood.administrativo.service.ConfirmacaoPedidoService;
import br.com.unipds.jfood.administrativo.service.CupomService;
import br.com.unipds.jfood.administrativo.service.DespachoService;
import br.com.unipds.jfood.administrativo.service.RecalculoValorService;
import br.com.unipds.jfood.administrativo.service.gateway.GatewayPagamentoSimulado;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Os endpoints da Aula 5. Estao juntos de proposito: sao os que a colecao do
 * Postman usa para reproduzir cada cenario de concorrencia.
 */
@RestController
@RequestMapping("/api/v1")
public class ConcorrenciaController {

    private final ConfirmacaoPedidoService confirmacaoService;
    private final CancelamentoPedidoService cancelamentoService;
    private final DespachoService despachoService;
    private final CupomService cupomService;
    private final RecalculoValorService recalculoService;
    private final AutoInvocacaoService autoInvocacaoService;
    private final GatewayPagamentoSimulado gateway;

    public ConcorrenciaController(ConfirmacaoPedidoService confirmacaoService,
                                  CancelamentoPedidoService cancelamentoService,
                                  DespachoService despachoService,
                                  CupomService cupomService,
                                  RecalculoValorService recalculoService,
                                  AutoInvocacaoService autoInvocacaoService,
                                  GatewayPagamentoSimulado gateway) {
        this.confirmacaoService = confirmacaoService;
        this.cancelamentoService = cancelamentoService;
        this.despachoService = despachoService;
        this.cupomService = cupomService;
        this.recalculoService = recalculoService;
        this.autoInvocacaoService = autoInvocacaoService;
        this.gateway = gateway;
    }

    /** Confirmacao com lock pessimista. Duas chamadas simultaneas: uma 200, uma 409. */
    @PostMapping("/pedidos/{pedidoId}/confirmar")
    public ResponseEntity<Map<String, Object>> confirmar(@PathVariable Long pedidoId) {
        Long pagamentoId = confirmacaoService.confirmarPedido(pedidoId);
        return ResponseEntity.ok(Map.of("pedidoId", pedidoId, "pagamentoId", pagamentoId));
    }

    /** A MESMA confirmacao, com a armadilha do self-invocation. Nao use como modelo. */
    @PostMapping("/pedidos/{pedidoId}/confirmar-errado")
    public ResponseEntity<Void> confirmarErrado(@PathVariable Long pedidoId) {
        autoInvocacaoService.confirmarComNotificacao(pedidoId);
        return ResponseEntity.noContent().build();
    }

    /** Cancelamento com controle otimista. Conflito devolve 409. */
    @PostMapping("/pedidos/{pedidoId}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable Long pedidoId) {
        cancelamentoService.cancelar(pedidoId);
        return ResponseEntity.noContent().build();
    }

    /** Uma rodada da fila de despacho. Rode em duas instancias ao mesmo tempo. */
    @PostMapping("/despacho/rodar")
    public ResponseEntity<List<Long>> despachar() {
        return ResponseEntity.ok(despachoService.despacharLote());
    }

    /** Resgate de cupom -- o caso de contencao maxima. */
    @PostMapping("/cupons/{codigo}/resgatar")
    public ResponseEntity<Map<String, BigDecimal>> resgatar(@PathVariable String codigo,
                                                           @RequestParam Long clienteId) {
        return ResponseEntity.ok(Map.of("desconto", cupomService.resgatar(codigo, clienteId)));
    }

    /** O job de background com retry e backoff. */
    @PostMapping("/pedidos/{pedidoId}/recalcular")
    public ResponseEntity<Void> recalcular(@PathVariable Long pedidoId) {
        recalculoService.recalcularValorTotal(pedidoId);
        return ResponseEntity.noContent().build();
    }

    /** Programa a proxima cobranca do pedido para ser recusada pelo gateway. */
    @PostMapping("/gateway/recusar/{pedidoId}")
    public ResponseEntity<Void> programarRecusa(@PathVariable Long pedidoId) {
        gateway.programarRecusa(pedidoId);
        return ResponseEntity.accepted().build();
    }
}
