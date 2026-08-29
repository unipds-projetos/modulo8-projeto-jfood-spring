package br.com.unipds.jfood.catalogo.web;

import br.com.unipds.jfood.catalogo.domain.Restaurante;
import br.com.unipds.jfood.catalogo.service.DestaqueService;
import br.com.unipds.jfood.catalogo.service.ExperimentoStampedeService;
import br.com.unipds.jfood.catalogo.service.VisualizacaoService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Os endpoints do experimento da Aula 9. */
@RestController
@RequestMapping("/api/v1")
public class CacheController {

    private final DestaqueService destaqueService;
    private final VisualizacaoService visualizacaoService;
    private final ExperimentoStampedeService experimentoService;

    public CacheController(DestaqueService destaqueService,
                           VisualizacaoService visualizacaoService,
                           ExperimentoStampedeService experimentoService) {
        this.destaqueService = destaqueService;
        this.visualizacaoService = visualizacaoService;
        this.experimentoService = experimentoService;
    }

    /** TTL fixo de 60 s: todas as replicas expiram no mesmo instante. */
    @GetMapping("/restaurantes/destaques")
    public ResponseEntity<List<Restaurante>> destaques() {
        return ResponseEntity.ok(destaqueService.destaquesSemJitter());
    }

    /** TTL de 60 s + ate 15 s de jitter: as expiracoes se espalham. */
    @GetMapping("/restaurantes/destaques-com-jitter")
    public ResponseEntity<List<Restaurante>> destaquesComJitter() {
        return ResponseEntity.ok(destaqueService.destaquesComJitter());
    }

    /** TTL de 60 s + trava de recalculo: so uma requisicao vai ao banco. */
    @GetMapping("/restaurantes/destaques-com-trava")
    public ResponseEntity<List<Restaurante>> destaquesComTrava() {
        return ResponseEntity.ok(destaqueService.destaquesComTrava());
    }

    /**
     * Dispara N requisicoes simultaneas no instante da expiracao e devolve
     * quantas chegaram ao MongoDB.
     */
    @PostMapping("/experimentos/stampede/rodar")
    public ResponseEntity<Map<String, Object>> rodarExperimento(
            @RequestParam ExperimentoStampedeService.Estrategia estrategia,
            @RequestParam(defaultValue = "200") int requisicoes) throws InterruptedException {
        return ResponseEntity.ok(experimentoService.rodar(estrategia, requisicoes));
    }

    /** Quantas vezes o MongoDB foi consultado desde o ultimo reinicio. */
    @GetMapping("/experimentos/stampede/acessos")
    public ResponseEntity<Map<String, Long>> acessos() {
        return ResponseEntity.ok(Map.of("acessosAoBanco", destaqueService.acessosAoBanco()));
    }

    @PostMapping("/experimentos/stampede/reiniciar")
    public ResponseEntity<Void> reiniciar() {
        destaqueService.reiniciarExperimento();
        return ResponseEntity.noContent().build();
    }

    /** Write-behind: incrementa no Redis, nao no MongoDB. */
    @PostMapping("/restaurantes/{id}/visualizacoes")
    public ResponseEntity<Map<String, Long>> visualizar(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("pendentesNoRedis",
                visualizacaoService.registrarVisualizacao(id)));
    }

    /** Dispara a consolidacao na mao (em producao quem faz e o @Scheduled). */
    @PostMapping("/experimentos/write-behind/consolidar")
    public ResponseEntity<Map<String, Long>> consolidar() {
        return ResponseEntity.ok(Map.of("consolidadas", visualizacaoService.consolidar()));
    }
}
