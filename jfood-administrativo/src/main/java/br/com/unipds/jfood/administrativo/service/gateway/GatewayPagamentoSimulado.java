package br.com.unipds.jfood.administrativo.service.gateway;

import br.com.unipds.jfood.administrativo.domain.MetodoPagamento;
import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;

/**
 * Gateway de mentira, para a aula.
 *
 * A recusa e controlavel de fora (POST /api/v1/gateway/recusar/{pedidoId}) para
 * que o cenario do REQUIRES_NEW possa ser provocado sem esperar o azar.
 */
@Component
public class GatewayPagamentoSimulado implements GatewayPagamento {

    private final Set<Long> pedidosQueSeraoRecusados = new CopyOnWriteArraySet<>();

    public void programarRecusa(Long pedidoId) {
        pedidosQueSeraoRecusados.add(pedidoId);
    }

    public void limparRecusas() {
        pedidosQueSeraoRecusados.clear();
    }

    @Override
    public void cobrar(Long pedidoId, MetodoPagamento metodo, BigDecimal valor) {
        if (pedidosQueSeraoRecusados.contains(pedidoId)) {
            throw new PagamentoRecusadoException(
                    "Operadora recusou a cobranca de R$ " + valor + " via " + metodo);
        }
    }
}
