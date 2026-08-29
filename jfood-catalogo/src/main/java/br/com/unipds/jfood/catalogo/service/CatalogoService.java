package br.com.unipds.jfood.catalogo.service;

import br.com.unipds.jfood.catalogo.domain.ItemCardapio;
import br.com.unipds.jfood.catalogo.domain.Restaurante;
import br.com.unipds.jfood.catalogo.repository.ItemCardapioRepository;
import br.com.unipds.jfood.catalogo.repository.RestauranteRepository;
import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class CatalogoService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoService.class);

    private final RestauranteRepository restauranteRepository;
    private final ItemCardapioRepository itemRepository;
    private final MongoTemplate mongoTemplate;

    public CatalogoService(RestauranteRepository restauranteRepository,
                           ItemCardapioRepository itemRepository,
                           MongoTemplate mongoTemplate) {
        this.restauranteRepository = restauranteRepository;
        this.itemRepository = itemRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Page<Restaurante> listar(Pageable pageable) {
        log.info("Consultando o MongoDB: listagem pagina {}", pageable.getPageNumber());
        return restauranteRepository.findAll(pageable);
    }

    public Page<Restaurante> listarPorCategoria(String categoria, Pageable pageable) {
        return restauranteRepository.findByCategoria(categoria, pageable);
    }

    public Page<Restaurante> buscarPorTagDeItem(String tag, Pageable pageable) {
        return restauranteRepository.findByTagDeItem(tag, pageable);
    }

    public Restaurante detalhar(String id) {
        log.info("Consultando o MongoDB: detalhe do restaurante {}", id);
        return restauranteRepository.findById(id)
                .orElseThrow(() -> new RestauranteNaoEncontradoException(id));
    }

    public Restaurante criar(Restaurante restaurante) {
        return restauranteRepository.save(restaurante);
    }

    public Restaurante atualizar(String id, Restaurante restaurante) {
        if (!restauranteRepository.existsById(id)) {
            throw new RestauranteNaoEncontradoException(id);
        }
        Restaurante comId = new Restaurante(id, restaurante.nome(), restaurante.categoria(),
                restaurante.fotoUrl(), restaurante.notaMedia(), restaurante.taxaEntrega(),
                restaurante.aberto(), restaurante.abreAs(), restaurante.fechaAs(),
                restaurante.visualizacoes(), restaurante.cardapio());

        Restaurante salvo = restauranteRepository.save(comId);
        propagarSubset(salvo);
        return salvo;
    }

    public void excluir(String id) {
        if (!restauranteRepository.existsById(id)) {
            throw new RestauranteNaoEncontradoException(id);
        }
        restauranteRepository.deleteById(id);
        itemRepository.deleteAll(
                itemRepository.findByRestauranteRestauranteId(id, Pageable.unpaged()).getContent());
    }

    // -------------------------------------------------------------------------
    // Subset Pattern
    // -------------------------------------------------------------------------

    /**
     * O preco da duplicacao: quando o restaurante muda de nome, de foto, de nota
     * ou de taxa, o subset copiado dentro de CADA item precisa ser atualizado.
     *
     * Um updateMany resolve, e o custo e proporcional ao numero de itens daquele
     * restaurante -- dezenas, nao milhoes. Se fosse um dado que muda toda hora, a
     * conta viraria outra: veja a discussao sobre taxa_entrega em docs/aula08.md.
     */
    public long propagarSubset(Restaurante restaurante) {
        UpdateResult resultado = mongoTemplate.updateMulti(
                Query.query(Criteria.where("restaurante.restauranteId").is(restaurante.id())),
                new Update()
                        .set("restaurante.nome", restaurante.nome())
                        .set("restaurante.fotoUrl", restaurante.fotoUrl())
                        .set("restaurante.notaMedia", restaurante.notaMedia())
                        .set("restaurante.taxaEntrega", restaurante.taxaEntrega()),
                ItemCardapio.class);

        log.info("Subset propagado para {} itens do restaurante {}",
                resultado.getModifiedCount(), restaurante.id());
        return resultado.getModifiedCount();
    }

    // -------------------------------------------------------------------------
    // A busca global por item -- o que motivou a colecao itens_cardapio
    // -------------------------------------------------------------------------

    public Page<ItemCardapio> buscarItens(String termo, Pageable pageable) {
        return itemRepository.findByNomeContainingIgnoreCaseAndDisponivelTrue(termo, pageable);
    }

    public Page<ItemCardapio> buscarItensPorTag(String tag, Pageable pageable) {
        return itemRepository.findByTagsContaining(tag, pageable);
    }
}
