package br.com.unipds.jfood.tracking.domain;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/** Sai o @Entity do mundo relacional, entra o @Table. */
@Table("rota_por_entregador_dia")
public record RotaPorEntregadorDia(

        @PrimaryKey RotaPorEntregadorDiaChave chave,

        @Column("latitude") Double latitude,
        @Column("longitude") Double longitude,
        @Column("velocidade_kmh") Double velocidadeKmh,
        @Column("entrega_id") Long entregaId
) {}
