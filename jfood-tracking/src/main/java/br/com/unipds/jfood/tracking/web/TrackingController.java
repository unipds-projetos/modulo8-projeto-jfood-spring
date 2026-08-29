package br.com.unipds.jfood.tracking.web;

import br.com.unipds.jfood.tracking.domain.PosicaoAtualEntregador;
import br.com.unipds.jfood.tracking.domain.RotaPorEntrega;
import br.com.unipds.jfood.tracking.domain.RotaPorEntregadorDia;
import br.com.unipds.jfood.tracking.service.CargaService;
import br.com.unipds.jfood.tracking.service.TrackingService;
import br.com.unipds.jfood.tracking.web.dto.PingRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final TrackingService trackingService;
    private final CargaService cargaService;

    public TrackingController(TrackingService trackingService, CargaService cargaService) {
        this.trackingService = trackingService;
        this.cargaService = cargaService;
    }

    /**
     * 202 ACCEPTED, e nao 201.
     *
     * O app do entregador dispara isto a cada 5 segundos e nao espera nada de
     * volta -- nao ha recurso a ser criado do ponto de vista dele, nao ha
     * Location para devolver, e ele nao vai tratar erro nenhum. 202 diz
     * exatamente isso: "recebi, vou processar, siga sua vida".
     */
    @PostMapping("/pings")
    public ResponseEntity<Void> registrar(@RequestBody PingRequest ping) {
        trackingService.registrarPing(ping);
        return ResponseEntity.accepted().build();
    }

    /** Pergunta 1 — onde esta o entregador agora? */
    @GetMapping("/entregadores/{entregadorId}/posicao")
    public ResponseEntity<PosicaoAtualEntregador> posicao(@PathVariable Long entregadorId) {
        PosicaoAtualEntregador posicao = trackingService.posicaoAtual(entregadorId);
        return posicao == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(posicao);
    }

    /** Pergunta 2 — qual foi a rota completa da entrega? */
    @GetMapping("/entregas/{entregaId}/rota")
    public ResponseEntity<List<RotaPorEntrega>> rotaDaEntrega(@PathVariable Long entregaId) {
        return ResponseEntity.ok(trackingService.rotaDaEntrega(entregaId));
    }

    /** Pergunta 3 — por onde o entregador passou no dia D? */
    @GetMapping("/entregadores/{entregadorId}/rota")
    public ResponseEntity<List<RotaPorEntregadorDia>> rotaDoDia(
            @PathVariable Long entregadorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return ResponseEntity.ok(trackingService.rotaDoDia(entregadorId, dia));
    }

    /** O custo do bucketing: sete leituras de particao, montadas aqui. */
    @GetMapping("/entregadores/{entregadorId}/rota-ultimos-dias")
    public ResponseEntity<Map<String, Object>> rotaUltimosDias(
            @PathVariable Long entregadorId,
            @RequestParam(defaultValue = "7") int dias) {
        long inicio = System.nanoTime();
        List<RotaPorEntregadorDia> pontos = trackingService.rotaDosUltimosDias(entregadorId, dias);
        return ResponseEntity.ok(Map.of(
                "dias", dias,
                "particoesLidas", dias,
                "pontos", pontos.size(),
                "duracaoMs", (System.nanoTime() - inicio) / 1_000_000));
    }

    /** O teste de carga da Etapa 10. */
    @PostMapping("/experimentos/carga")
    public ResponseEntity<Map<String, Object>> carga(
            @RequestParam(defaultValue = "100000") int pontos,
            @RequestParam(defaultValue = "200") int entregadores,
            @RequestParam(defaultValue = "32") int concorrencia) throws InterruptedException {
        return ResponseEntity.ok(cargaService.disparar(pontos, entregadores, concorrencia));
    }
}
