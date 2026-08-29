package br.com.unipds.jfood.catalogo.repository;

import br.com.unipds.jfood.catalogo.domain.Restaurante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface RestauranteRepository extends MongoRepository<Restaurante, String> {

    /** Page, e nao List: nenhuma listagem vai para producao sem Pageable. */
    Page<Restaurante> findByCategoria(String categoria, Pageable pageable);

    Page<Restaurante> findByNomeContainingIgnoreCase(String termo, Pageable pageable);

    /**
     * Notacao de ponto: navega para dentro dos subdocumentos como se fosse um
     * caminho de arquivo. O $elemMatch nao e necessario aqui porque a condicao e
     * de um campo so.
     */
    @Query("{ 'cardapio.itens.tags': ?0 }")
    Page<Restaurante> findByTagDeItem(String tag, Pageable pageable);
}
