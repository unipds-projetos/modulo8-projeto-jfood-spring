package br.com.unipds.jfood.catalogo.repository;

import br.com.unipds.jfood.catalogo.domain.ItemCardapio;
import br.com.unipds.jfood.catalogo.domain.Restaurante;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Os indices, criados EXPLICITAMENTE.
 *
 * Por que nao auto-index-creation=true: com ele, cada instancia da aplicacao
 * tenta criar os indices na subida. Numa colecao grande isso e uma operacao de
 * minutos que BLOQUEIA escrita -- disparada por um deploy, sem ninguem decidir,
 * em horario que ninguem escolheu. Pior: a criacao vira efeito colateral de uma
 * anotacao, e some da revisao de codigo.
 *
 * Em producao o indice e criado com createIndex({...}, {background: true}) por
 * quem opera, em janela combinada. Este bean e o meio-termo honesto para a aula:
 * a criacao continua explicita, versionada e visivel no log.
 */
@Configuration
public class MongoIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    @Bean
    ApplicationRunner criarIndices(MongoTemplate mongoTemplate) {
        return args -> {
            // Indexados: os campos por que a tela FILTRA e ORDENA.
            mongoTemplate.indexOps(Restaurante.class)
                    .createIndex(new Index().on("nome", Sort.Direction.ASC).named("idx_restaurante_nome"));
            mongoTemplate.indexOps(Restaurante.class)
                    .createIndex(new Index().on("categoria", Sort.Direction.ASC)
                                            .on("nota_media", Sort.Direction.DESC)
                                            .named("idx_restaurante_categoria_nota"));

            mongoTemplate.indexOps(ItemCardapio.class)
                    .createIndex(new Index().on("nome", Sort.Direction.ASC).named("idx_item_nome"));
            mongoTemplate.indexOps(ItemCardapio.class)
                    .createIndex(new Index().on("tags", Sort.Direction.ASC).named("idx_item_tags"));

            // NAO indexados de proposito: descricao, foto_url, opcionais, horarios.
            // Ninguem filtra por eles, e todo indice e escrita a mais em todo insert.
            log.info("Indices do catalogo verificados");
        };
    }
}
