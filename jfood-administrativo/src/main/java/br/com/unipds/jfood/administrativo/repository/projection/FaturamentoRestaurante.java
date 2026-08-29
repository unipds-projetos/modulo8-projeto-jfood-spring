package br.com.unipds.jfood.administrativo.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projecao sobre a materialized view.
 *
 * A coluna atualizado_em nao e enfeite: o consumidor precisa saber QUAO VELHO e
 * o dado que esta vendo. Sem ela, um refresh travado passa despercebido e o
 * painel exibe numeros antigos como se fossem de agora.
 */
public interface FaturamentoRestaurante {

    Long getRestauranteId();

    String getRestaurante();

    String getCategoria();

    Long getPedidosEntregues();

    BigDecimal getReceitaTotal();

    BigDecimal getTicketMedio();

    /**
     * Instant, e nao OffsetDateTime.
     *
     * A consulta e NATIVA: o Hibernate devolve o timestamptz do PostgreSQL como
     * java.time.Instant, e a projecao por interface NAO converte tipos -- ela so
     * repassa o que veio. Declarar OffsetDateTime aqui faz a consulta rodar, a
     * lista voltar preenchida, e a serializacao do JSON estourar so na saida:
     *
     *   HttpMessageNotWritableException: Could not write JSON: Cannot project
     *   java.time.Instant to java.time.OffsetDateTime; Target type is not an
     *   interface and no matching Converter found
     *
     * -- um 500 sem stack trace no log de erro, porque o Spring MVC resolve a
     * excecao como WARN. Nas entidades JPA (@Column) a conversao acontece; em
     * projecao sobre query nativa, nao.
     */
    Instant getAtualizadoEm();
}
