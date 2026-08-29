package br.com.unipds.jfood.tracking.domain;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("rota_por_entrega")
public record RotaPorEntrega(

        @PrimaryKey RotaPorEntregaChave chave,

        @Column("entregador_id") Long entregadorId,
        @Column("latitude") Double latitude,
        @Column("longitude") Double longitude,
        @Column("velocidade_kmh") Double velocidadeKmh
) {}
