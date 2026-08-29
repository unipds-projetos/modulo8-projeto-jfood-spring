package br.com.unipds.jfood.catalogo.web;

import br.com.unipds.jfood.catalogo.service.RelatorioService;
import br.com.unipds.jfood.catalogo.web.dto.RelatorioPorCategoria;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping("/preco-por-categoria")
    public ResponseEntity<List<RelatorioPorCategoria>> precoPorCategoria() {
        return ResponseEntity.ok(relatorioService.precoMedioPorCategoria());
    }
}
