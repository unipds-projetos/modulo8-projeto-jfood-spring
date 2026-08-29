package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.MetodoPagamento;
import br.com.unipds.jfood.administrativo.domain.Pagamento;
import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.domain.StatusPagamento;
import br.com.unipds.jfood.administrativo.domain.StatusPedido;
import br.com.unipds.jfood.administrativo.repository.PagamentoRepository;
import br.com.unipds.jfood.administrativo.repository.PedidoRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A ARMADILHA DO SELF-INVOCATION, deixada no codigo de proposito.
 *
 * O @Transactional funciona por PROXY (Spring AOP). Quando um bean chama um
 * metodo DE SI MESMO, a chamada passa por `this` e nao pelo proxy -- a anotacao
 * e simplesmente ignorada.
 *
 * A correcao esta em ConfirmacaoPedidoService: o metodo transacional mora em
 * OUTRO bean, e a chamada passa pelo proxy.
 *
 * O endpoint /api/v1/pedidos/{id}/confirmar-errado existe para provar a
 * inconsistencia. O resultado medido esta em docs/aula05.md.
 */
@Service
public class AutoInvocacaoService {

    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;

    public AutoInvocacaoService(PedidoRepository pedidoRepository,
                                PagamentoRepository pagamentoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.pagamentoRepository = pagamentoRepository;
    }

    /** ERRADO: a chamada abaixo bypassa o proxy AOP. */
    public void confirmarComNotificacao(Long pedidoId) {
        this.confirmar(pedidoId);          // <- sem proxy, sem transacao
        notificarCliente(pedidoId);
    }

    /** @Transactional IGNORADO quando chamado pela linha acima. */
    @Transactional
    public void confirmar(Long pedidoId) {
        Pedido pedido = pedidoRepository.buscarParaAtualizacao(pedidoId)
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));

        if (pedido.getStatus() != StatusPedido.CRIADO) {
            throw new StatusPedidoInvalidoException(pedidoId, pedido.getStatus(), "confirmado");
        }

        // Sem transacao, a entidade ja voltou DETACHED do repositorio: este setter
        // altera memoria e nada mais. Nenhum dirty checking vai rodar.
        pedido.setStatus(StatusPedido.CONFIRMADO);

        Pagamento pagamento = new Pagamento();
        pagamento.setPedido(pedido);
        pagamento.setMetodo(MetodoPagamento.PIX);
        pagamento.setValor(pedido.getValorTotal());
        pagamento.setStatus(StatusPagamento.APROVADO);
        pagamento.setPagoEm(OffsetDateTime.now());
        // save() TEM transacao propria (a do SimpleJpaRepository): esta linha grava.
        pagamentoRepository.save(pagamento);
    }

    private void notificarCliente(Long pedidoId) {
        // aqui iria o push / e-mail
    }
}
