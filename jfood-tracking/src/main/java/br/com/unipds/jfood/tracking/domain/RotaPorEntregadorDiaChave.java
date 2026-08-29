package br.com.unipds.jfood.tracking.domain;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

/**
 * A chave nao e um @Id simples: ela CARREGA RESPONSABILIDADE FISICA no cluster,
 * entao ganha classe propria.
 *
 * PARTITIONED decide em qual NO o dado e escrito. CLUSTERED decide a ordem no
 * disco DENTRO da particao. Nao e decoracao -- e a diferenca entre um cluster
 * equilibrado e um no sozinho recebendo 40.000 escritas por segundo.
 *
 * O `dia` faz parte da PARTICAO, e nao da clustering key: e ele que limita o
 * tamanho da particao a uma jornada de trabalho.
 */
@PrimaryKeyClass
public record RotaPorEntregadorDiaChave(

        @PrimaryKeyColumn(name = "entregador_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        Long entregadorId,

        @PrimaryKeyColumn(name = "dia", type = PrimaryKeyType.PARTITIONED, ordinal = 1)
        LocalDate dia,

        /**
         * Instant, e nao LocalDateTime.
         *
         * O Cassandra armazena TIMESTAMP como instante UTC. LocalDateTime nao
         * carrega fuso -- e entregadores em fusos diferentes e o caso NORMAL em
         * um app nacional. Com LocalDateTime, um ping de Manaus e outro de Sao
         * Paulo no mesmo segundo aparecem com uma hora de diferenca na rota.
         */
        @PrimaryKeyColumn(name = "instante", type = PrimaryKeyType.CLUSTERED,
                          ordinal = 2, ordering = Ordering.DESCENDING)
        Instant instante

) implements Serializable {
    // record ja gera equals e hashCode com todos os componentes
}
