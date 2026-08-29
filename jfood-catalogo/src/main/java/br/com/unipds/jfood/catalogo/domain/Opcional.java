package br.com.unipds.jfood.catalogo.domain;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * O motivo de o cardapio ter saido do PostgreSQL.
 *
 * "Tamanho" tem P/M/G; "ponto da carne" tem cinco valores; "adicionais" tem
 * quinze e permite escolher varios. Cada restaurante inventa os seus. Em
 * relacional isso vira uma tabela EAV ou quarenta colunas nulas.
 *
 * Subdocumento: POJO puro, sem @Document e sem @Id.
 */
public record Opcional(
        @Field("nome") String nome,
        @Field("obrigatorio") boolean obrigatorio,
        @Field("minimo_escolhas") int minimoEscolhas,
        @Field("maximo_escolhas") int maximoEscolhas,
        @Field("escolhas") List<Escolha> escolhas
) {

    public record Escolha(
            @Field("nome") String nome,
            @Field("acrescimo") BigDecimal acrescimo) {}
}
