package br.com.unipds.jfood.administrativo.web;

import br.com.unipds.jfood.administrativo.repository.projection.FaturamentoRestaurante;
import br.com.unipds.jfood.administrativo.service.FaturamentoService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/relatorios")
public class RelatorioController {

    private final FaturamentoService faturamentoService;

    public RelatorioController(FaturamentoService faturamentoService) {
        this.faturamentoService = faturamentoService;
    }

    /** Le o snapshot da materialized view -- barato e defasado. */
    @GetMapping("/faturamento")
    public ResponseEntity<List<FaturamentoRestaurante>> faturamento() {
        return ResponseEntity.ok(faturamentoService.lerSnapshot());
    }

    /** Dispara o refresh na mao (em producao quem faz isso e o @Scheduled). */
    @PostMapping("/faturamento/atualizar")
    public ResponseEntity<Void> atualizar() {
        faturamentoService.atualizarSnapshot();
        return ResponseEntity.noContent().build();
    }
}
