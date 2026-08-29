package br.com.unipds.jfood.catalogo.domain;

import java.math.BigDecimal;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * O SUBSET do restaurante, duplicado dentro de cada item de cardapio.
 *
 * Exatamente os campos que o card do resultado de busca mostra -- e nenhum a
 * mais. Cada campo aqui e um campo que precisa ser propagado quando muda; a
 * disciplina de manter este record pequeno e o que torna o padrao sustentavel.
 */
public record RestauranteResumo(
        // targetType = OBJECT_ID e o que faz este String virar ObjectId no BSON --
        // nas GRAVACOES e, o que importa mais, nas CONSULTAS. Sem ele, o
        // updateMulti procura pela string "6a93..." e nao acha o ObjectId("6a93..."):
        // nenhum erro, nenhum log, zero documentos atualizados.
        @Field(value = "restaurante_id", targetType = FieldType.OBJECT_ID) String restauranteId,
        @Field("nome") String nome,
        @Field("foto_url") String fotoUrl,
        @Field("nota_media") BigDecimal notaMedia,
        @Field("taxa_entrega") BigDecimal taxaEntrega
) {}
