package br.com.unipds.jfood.catalogo.domain;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * O item como ele aparece DENTRO do documento do restaurante.
 *
 * Depois do Subset Pattern (secao 8.8 da apostila) ele carrega o item_id, que
 * aponta para o documento completo na colecao itens_cardapio. O resumo e o que a
 * tela do restaurante precisa; o completo e o que a tela do item precisa.
 */
public record ItemResumo(
        @Field(value = "item_id", targetType = FieldType.OBJECT_ID) String itemId,
        @Field("nome") String nome,
        @Field("descricao") String descricao,
        @Field("preco") BigDecimal preco,
        @Field("foto_url") String fotoUrl,
        @Field("tags") List<String> tags,
        @Field("disponivel") boolean disponivel,
        @Field("opcionais") List<Opcional> opcionais
) {}
