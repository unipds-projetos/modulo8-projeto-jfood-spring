package br.com.unipds.jfood.catalogo.repository;

import br.com.unipds.jfood.catalogo.domain.ItemCardapio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ItemCardapioRepository extends MongoRepository<ItemCardapio, String> {

    /** A busca global por item: "quero pizza de calabresa". */
    Page<ItemCardapio> findByNomeContainingIgnoreCaseAndDisponivelTrue(String termo, Pageable pageable);

    Page<ItemCardapio> findByTagsContaining(String tag, Pageable pageable);

    Page<ItemCardapio> findByRestauranteRestauranteId(String restauranteId, Pageable pageable);
}
