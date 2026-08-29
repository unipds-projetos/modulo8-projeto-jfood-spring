package br.com.unipds.jfood.recomendacoes.web;

import br.com.unipds.jfood.recomendacoes.repository.ClienteRepository;
import br.com.unipds.jfood.recomendacoes.repository.ContaVinculada;
import br.com.unipds.jfood.recomendacoes.repository.RecomendacaoRestaurante;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recomendacoes")
public class RecomendacaoController {

    private final ClienteRepository clienteRepository;

    public RecomendacaoController(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    /** Ranking por contagem de vizinhos. */
    @GetMapping("/clientes/{clienteId}")
    public ResponseEntity<List<RecomendacaoRestaurante>> recomendar(
            @PathVariable Long clienteId,
            @RequestParam(defaultValue = "4") int notaMinima,
            @RequestParam(defaultValue = "5") int limite) {

        List<RecomendacaoRestaurante> recomendacoes =
                clienteRepository.recomendarPorVizinhanca(clienteId, notaMinima, limite);

        // 204 e nao 200 com lista vazia: cliente novo, sem avaliacoes, nao tem
        // vizinhanca -- e o app precisa saber a diferenca entre "nao ha
        // recomendacao" e "ha zero restaurantes".
        return recomendacoes.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(recomendacoes);
    }

    /** Ranking por nota ponderada, com desempate por categoria. */
    @GetMapping("/clientes/{clienteId}/ponderado")
    public ResponseEntity<List<RecomendacaoRestaurante>> recomendarPonderado(
            @PathVariable Long clienteId,
            @RequestParam(defaultValue = "4") int notaMinima,
            @RequestParam(defaultValue = "5") int limite) {

        List<RecomendacaoRestaurante> recomendacoes =
                clienteRepository.recomendarPonderado(clienteId, notaMinima, limite);

        return recomendacoes.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(recomendacoes);
    }

    /** Contas que compartilham cartao ou dispositivo. */
    @GetMapping("/fraude/contas-vinculadas")
    public ResponseEntity<List<ContaVinculada>> contasVinculadas() {
        return ResponseEntity.ok(clienteRepository.contasVinculadas());
    }
}
