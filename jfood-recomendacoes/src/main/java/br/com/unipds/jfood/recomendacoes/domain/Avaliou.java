package br.com.unipds.jfood.recomendacoes.domain;

import java.time.LocalDate;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * A NOTA VAI NA ARESTA -- e essa e a decisao de modelagem da etapa.
 *
 * A nota nao e propriedade do cliente ("a nota da Ana") nem do restaurante ("a
 * nota da Cantina"): ela so existe no ENCONTRO dos dois. Em MER seria um
 * relacionamento N:N com atributo -- exatamente o item_pedido da Etapa 1.
 *
 * Poe-la em um no intermediario (:Avaliacao) funcionaria, e custaria um salto a
 * mais em toda travessia -- em uma consulta de tres saltos, isso vira seis. A
 * aresta com propriedade e o que o Property Graph Model existe para expressar.
 *
 * Tres anotacoes, tres papeis: @RelationshipProperties marca a aresta,
 * @TargetNode diz para onde ela aponta, @RelationshipId e a identidade interna.
 */
@RelationshipProperties
public record Avaliou(

        @RelationshipId String id,
        @TargetNode Restaurante restaurante,

        int nota,
        LocalDate data
) {}
