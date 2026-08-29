package br.com.unipds.jfood.catalogo.domain;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * O documento raiz do catalogo.
 *
 * Toda a tela do restaurante -- nome, foto, categoria, horario, taxa e o cardapio
 * inteiro dividido em secoes -- vem em UMA leitura de UM documento. Era esse o
 * objetivo da modelagem, e e o criterio de pronto da Etapa 8.
 */
@Document(collection = "restaurantes")
public record Restaurante(

        @Id String id,                       // ObjectId, e nao o BIGSERIAL do JPA

        @Field("nome") String nome,
        @Field("categoria") String categoria,
        @Field("foto_url") String fotoUrl,
        @Field("nota_media") BigDecimal notaMedia,
        @Field("taxa_entrega") BigDecimal taxaEntrega,
        @Field("aberto") boolean aberto,
        @Field("abre_as") LocalTime abreAs,
        @Field("fecha_as") LocalTime fechaAs,
        @Field("visualizacoes") Long visualizacoes,
        @Field("cardapio") List<SecaoCardapio> cardapio
) {

    /**
     * @Field explicito em TODOS os campos, mesmo quando o nome coincide.
     *
     * Sem ele, o nome da propriedade Java vira o nome do campo no BSON -- e um
     * refactor inocente de "notaMedia" para "nota" faz a aplicacao parar de
     * enxergar dados que continuam la. O mapeamento passa a ser uma decisao
     * explicita, versionada junto com o codigo.
     */
    public Restaurante comVisualizacoes(Long novasVisualizacoes) {
        return new Restaurante(id, nome, categoria, fotoUrl, notaMedia, taxaEntrega,
                aberto, abreAs, fechaAs, novasVisualizacoes, cardapio);
    }
}
