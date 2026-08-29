package br.com.unipds.jfood.catalogo.domain;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A colecao que o SUBSET PATTERN criou.
 *
 * O JFood lancou a busca global: "quero pizza de calabresa perto de mim" -- uma
 * busca por ITEM, e nao por restaurante. Com os itens so embutidos, responde-la
 * exigiria varrer todos os restaurantes e abrir cada cardapio.
 *
 * Entao o item ganha documento proprio, e leva DENTRO dele o subset do
 * restaurante que a tela de resultado precisa. Uma consulta, um documento.
 */
@Document(collection = "itens_cardapio")
public record ItemCardapio(

        @Id String id,

        @Field("nome") String nome,
        @Field("descricao") String descricao,
        @Field("preco") BigDecimal preco,
        @Field("foto_url") String fotoUrl,
        @Field("tags") List<String> tags,
        @Field("secao") String secao,
        @Field("disponivel") boolean disponivel,
        @Field("opcionais") List<Opcional> opcionais,

        // O subset duplicado. NAO e o restaurante inteiro: e o que o card mostra.
        @Field("restaurante") RestauranteResumo restaurante
) {}
