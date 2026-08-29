package br.com.unipds.jfood.catalogo.web;

import br.com.unipds.jfood.catalogo.domain.ItemCardapio;
import br.com.unipds.jfood.catalogo.domain.Restaurante;
import br.com.unipds.jfood.catalogo.service.CatalogoService;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurantes")
public class RestauranteController {

    private final CatalogoService catalogoService;

    public RestauranteController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    /**
     * A tela inicial do delivery mostra os MAIS BEM AVALIADOS, e nao a ordem de
     * insercao. O @PageableDefault e onde essa decisao de produto vira codigo.
     */
    @GetMapping
    public ResponseEntity<Page<Restaurante>> listar(
            @PageableDefault(size = 20, sort = "notaMedia", direction = Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(catalogoService.listar(pageable));
    }

    @GetMapping("/por-categoria")
    public ResponseEntity<Page<Restaurante>> listarPorCategoria(
            @RequestParam String categoria,
            @PageableDefault(size = 20, sort = "notaMedia", direction = Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(catalogoService.listarPorCategoria(categoria, pageable));
    }

    /** Notacao de ponto: restaurantes que tem algum item com a tag pedida. */
    @GetMapping("/com-item-tag")
    public ResponseEntity<Page<Restaurante>> comItemDaTag(
            @RequestParam String tag,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(catalogoService.buscarPorTagDeItem(tag, pageable));
    }

    /** A tela do restaurante: UMA consulta, UM documento. */
    @GetMapping("/{id}")
    public ResponseEntity<Restaurante> detalhar(@PathVariable String id) {
        return ResponseEntity.ok(catalogoService.detalhar(id));
    }

    @PostMapping
    public ResponseEntity<Restaurante> criar(@RequestBody Restaurante restaurante) {
        Restaurante salvo = catalogoService.criar(restaurante);
        return ResponseEntity.created(URI.create("/api/v1/restaurantes/" + salvo.id())).body(salvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Restaurante> atualizar(@PathVariable String id,
                                                 @RequestBody Restaurante restaurante) {
        return ResponseEntity.ok(catalogoService.atualizar(id, restaurante));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable String id) {
        catalogoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    /** A busca global por item -- a tela que motivou a colecao itens_cardapio. */
    @GetMapping("/itens/busca")
    public ResponseEntity<Page<ItemCardapio>> buscarItens(
            @RequestParam String termo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(catalogoService.buscarItens(termo, pageable));
    }

    @GetMapping("/itens/por-tag")
    public ResponseEntity<Page<ItemCardapio>> buscarItensPorTag(
            @RequestParam String tag,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(catalogoService.buscarItensPorTag(tag, pageable));
    }
}
