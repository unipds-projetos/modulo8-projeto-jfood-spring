package br.com.unipds.jfood.tracking.domain;

import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

/**
 * Sem bucket aqui, e de proposito: uma corrida dura de 15 a 60 minutos, entao a
 * particao nasce limitada (180 a 720 pontos) pelo proprio dominio.
 *
 * Ordem ASC: a rota e reconstruida do inicio ao fim.
 */
@PrimaryKeyClass
public record RotaPorEntregaChave(

        @PrimaryKeyColumn(name = "entrega_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        Long entregaId,

        @PrimaryKeyColumn(name = "instante", type = PrimaryKeyType.CLUSTERED,
                          ordinal = 1, ordering = Ordering.ASCENDING)
        Instant instante

) implements Serializable {
}
