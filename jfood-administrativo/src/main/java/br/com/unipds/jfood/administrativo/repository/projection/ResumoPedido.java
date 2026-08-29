package br.com.unipds.jfood.administrativo.repository.projection;

import java.math.BigDecimal;

/**
 * Projecao por interface para a tela de historico.
 *
 * A entidade Pedido inteira traria as FKs, a observacao e a taxa -- e, atras dos
 * proxies, um caminho aberto para o cliente e o endereco de entrega. A tela mostra
 * tres campos; a consulta traz tres colunas, e a entidade nem chega a ser criada.
 */
public interface ResumoPedido {

    Long getNumero();

    String getRestaurante();

    BigDecimal getValorTotal();
}
