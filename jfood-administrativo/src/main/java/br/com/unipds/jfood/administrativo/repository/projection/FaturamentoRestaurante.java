package br.com.unipds.jfood.administrativo.repository.projection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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

    OffsetDateTime getAtualizadoEm();
}
