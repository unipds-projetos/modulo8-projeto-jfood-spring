package br.com.unipds.jfood.tracking.domain;

import java.time.Instant;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * Uma linha por entregador, sobrescrita a cada ping.
 *
 * No Cassandra, INSERT e UPDATE sao a mesma operacao (upsert): sobrescrever nao
 * custa mais caro do que inserir, e nao gera tombstone.
 */
@Table("posicao_atual_entregador")
public record PosicaoAtualEntregador(

        @PrimaryKey("entregador_id") Long entregadorId,

        @Column("instante") Instant instante,
        @Column("latitude") Double latitude,
        @Column("longitude") Double longitude,
        @Column("velocidade_kmh") Double velocidadeKmh,
        @Column("entrega_id") Long entregaId
) {}
