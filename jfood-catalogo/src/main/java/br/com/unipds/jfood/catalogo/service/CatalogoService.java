package br.com.unipds.jfood.catalogo.service;

import br.com.unipds.jfood.catalogo.domain.ItemCardapio;
import br.com.unipds.jfood.catalogo.domain.PaginaRestaurantes;
import br.com.unipds.jfood.catalogo.domain.Restaurante;
import br.com.unipds.jfood.catalogo.repository.ItemCardapioRepository;
import br.com.unipds.jfood.catalogo.repository.RestauranteRepository;
import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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

    /**
     * CACHE ASIDE na listagem.
     *
     * A chave e explicita por SpEL. Deixar o toString() do Pageable virar chave
     * produz coisas como "Page request [number: 0, size: 20, sort: nota_media:
     * DESC]" -- que funciona, mas e ilegivel no redis-cli, muda de formato entre
     * versoes do Spring Data e vaza detalhe interno para dentro do banco.
     */
    /**
     * ATENCAO: nao envolva este metodo em outro metodo DESTE bean que faca a
     * conversao para Page. A chamada interna passaria por `this` e nao pelo
     * proxy, e o @Cacheable seria ignorado -- a mesma armadilha de
     * self-invocation da Aula 5, agora com cache em vez de transacao. Quem
     * converte e o controller.
     */
    @Cacheable(cacheNames = "restaurantes-listagem",
               key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public PaginaRestaurantes listar(Pageable pageable) {
        log.info("MISS -> consultando o MongoDB: listagem pagina {}", pageable.getPageNumber());
        return PaginaRestaurantes.de(restauranteRepository.findAll(pageable));
    }

    public Page<Restaurante> listarPorCategoria(String categoria, Pageable pageable) {
        return restauranteRepository.findByCategoria(categoria, pageable);
    }

    public Page<Restaurante> buscarPorTagDeItem(String tag, Pageable pageable) {
        return restauranteRepository.findByTagDeItem(tag, pageable);
    }

    /**
     * CACHE ASIDE no detalhe -- a tela mais aberta do app.
     *
     * A linha de log so aparece no MISS: se ela some da segunda chamada em
     * diante, o cache esta funcionando. E o jeito mais barato de instrumentar.
     */
    @Cacheable(cacheNames = "restaurante-detalhe", key = "#id")
    public Restaurante detalhar(String id) {
        log.info("MISS -> consultando o MongoDB: detalhe do restaurante {}", id);
        return restauranteRepository.findById(id)
                .orElseThrow(() -> new RestauranteNaoEncontradoException(id));
    }

    /**
     * Criar invalida as LISTAGENS (o restaurante novo tem de aparecer), mas nao
     * ha detalhe em cache para invalidar -- ele ainda nao existia.
     */
    @CacheEvict(cacheNames = "restaurantes-listagem", allEntries = true)
    public Restaurante criar(Restaurante restaurante) {
        return restauranteRepository.save(restaurante);
    }

    /**
     * INVALIDACAO CORRETA: o detalhe daquele restaurante E todas as listagens.
     *
     * Esquecer o segundo evict e o erro classico -- o detalhe volta certo e a
     * listagem continua mostrando o nome antigo, ate o TTL de 60 min expirar.
     * O allEntries e a resposta honesta: nao da para saber em quais paginas
     * aquele restaurante aparecia depois que a ordenacao mudou.
     *
     * @CachePut nao serve aqui: ele grava o retorno do metodo em UMA chave, e o
     * problema sao N chaves de listagem que ficaram erradas de uma vez.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "restaurante-detalhe", key = "#id"),
            @CacheEvict(cacheNames = "restaurantes-listagem", allEntries = true),
            @CacheEvict(cacheNames = "restaurantes-destaque", allEntries = true)
    })
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

    @Caching(evict = {
            @CacheEvict(cacheNames = "restaurante-detalhe", key = "#id"),
            @CacheEvict(cacheNames = "restaurantes-listagem", allEntries = true),
            @CacheEvict(cacheNames = "restaurantes-destaque", allEntries = true)
    })
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
