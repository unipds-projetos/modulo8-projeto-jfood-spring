package br.com.unipds.jfood.administrativo.web;

import br.com.unipds.jfood.administrativo.service.RestauranteService;
import br.com.unipds.jfood.administrativo.web.dto.RestauranteResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurantes")
public class RestauranteController {

    private final RestauranteService restauranteService;

    public RestauranteController(RestauranteService restauranteService) {
        this.restauranteService = restauranteService;
    }

    @GetMapping("/busca")
    public ResponseEntity<List<RestauranteResponse>> buscar(@RequestParam String termo) {
        return ResponseEntity.ok(restauranteService.buscar(termo));
    }
}
