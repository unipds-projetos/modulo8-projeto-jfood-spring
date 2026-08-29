package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.repository.RestauranteRepository;
import br.com.unipds.jfood.administrativo.web.dto.RestauranteResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestauranteService {

    private final RestauranteRepository restauranteRepository;

    public RestauranteService(RestauranteRepository restauranteRepository) {
        this.restauranteRepository = restauranteRepository;
    }

    /** Busca textual: a query nativa da forma 3. */
    @Transactional(readOnly = true)
    public List<RestauranteResponse> buscar(String termo) {
        return restauranteRepository.buscaTextualPorNome(termo)
                .stream()
                .map(r -> new RestauranteResponse(r.getId(), r.getNome(), r.getCep()))
                .toList();
    }
}
