package br.com.unipds.jfood.administrativo.service.gateway;

import br.com.unipds.jfood.administrativo.domain.MetodoPagamento;
import java.math.BigDecimal;

public interface GatewayPagamento {

    /** Lanca PagamentoRecusadoException quando a operadora nega. */
    void cobrar(Long pedidoId, MetodoPagamento metodo, BigDecimal valor);
}
