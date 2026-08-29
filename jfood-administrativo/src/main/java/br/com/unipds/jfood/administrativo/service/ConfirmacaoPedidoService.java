package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.MetodoPagamento;
import br.com.unipds.jfood.administrativo.domain.Pagamento;
import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.domain.StatusPagamento;
import br.com.unipds.jfood.administrativo.domain.StatusPedido;
import br.com.unipds.jfood.administrativo.repository.PagamentoRepository;
import br.com.unipds.jfood.administrativo.repository.PedidoRepository;
import br.com.unipds.jfood.administrativo.service.gateway.GatewayPagamento;
import br.com.unipds.jfood.administrativo.service.gateway.PagamentoRecusadoException;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean proprio, e nao um metodo do PedidoService — e a correcao da armadilha do
 * self-invocation demonstrada em PedidoService.confirmarComNotificacao.
 */
@Service
public class ConfirmacaoPedidoService {

    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final GatewayPagamento gateway;
    private final LogPagamentoService logService;

    public ConfirmacaoPedidoService(PedidoRepository pedidoRepository,
                                    PagamentoRepository pagamentoRepository,
                                    GatewayPagamento gateway,
                                    LogPagamentoService logService) {
        this.pedidoRepository = pedidoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.gateway = gateway;
        this.logService = logService;
    }

    /**
     * A ordem dos passos e a aula inteira:
     *
     *   1. adquire o lock   -- quem chegar agora espera aqui
     *   2. SO ENTAO decide  -- le o status ja protegido
     *   3. grava            -- status + pagamento, na mesma transacao
     *
     * A thread B que chegar durante o processamento de A trava no passo 1. Quando
     * A commita, B acorda, le CONFIRMADO, cai no if e recebe erro. Zero duplo
     * pagamento. O custo: B esperou.
     */
    @Transactional
    public Long confirmarPedido(Long pedidoId) {
        Pedido pedido = pedidoRepository.buscarParaAtualizacao(pedidoId)     // 1
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));

        if (pedido.getStatus() != StatusPedido.CRIADO) {                     // 2
            throw new StatusPedidoInvalidoException(pedidoId, pedido.getStatus(), "confirmado");
        }

        try {
            gateway.cobrar(pedidoId, MetodoPagamento.PIX, pedido.getValorTotal());
        } catch (PagamentoRecusadoException e) {
            // Transacao propria: o log persiste mesmo com o rollback externo.
            logService.registrarFalha(pedidoId, e.getMessage());
            throw e;   // propaga para reverter tudo o que veio antes
        }

        pedido.setStatus(StatusPedido.CONFIRMADO);                           // 3

        Pagamento pagamento = new Pagamento();
        pagamento.setPedido(pedido);
        pagamento.setMetodo(MetodoPagamento.PIX);
        pagamento.setValor(pedido.getValorTotal());
        pagamento.setStatus(StatusPagamento.APROVADO);
        pagamento.setPagoEm(OffsetDateTime.now());
        pagamentoRepository.save(pagamento);

        // O COMMIT libera o lock automaticamente.
        return pagamento.getId();
    }
}
