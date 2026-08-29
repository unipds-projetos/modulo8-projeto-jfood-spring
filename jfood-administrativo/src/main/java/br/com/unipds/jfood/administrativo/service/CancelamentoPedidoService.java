package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.domain.StatusPedido;
import br.com.unipds.jfood.administrativo.repository.PedidoRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelamentoPedidoService {

    private static final Set<StatusPedido> CANCELAVEIS =
            EnumSet.of(StatusPedido.CRIADO, StatusPedido.CONFIRMADO, StatusPedido.EM_PREPARO);

    private final PedidoRepository pedidoRepository;

    public CancelamentoPedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    /**
     * Cancelamento com controle OTIMISTA: nenhum lock e adquirido.
     *
     * O conflito e detectado na ESCRITA -- o Hibernate emite
     *   UPDATE pedido SET status = ?, versao = 4 WHERE id = ? AND versao = 3
     * e, se voltar zero linha, lanca OptimisticLockingFailureException.
     *
     * Cabe otimista aqui porque a contencao e baixa: dois cancelamentos
     * simultaneos do MESMO pedido sao raros (e o mesmo cliente clicando duas
     * vezes). Ninguem espera; quem perder, refaz.
     */
    @Transactional
    public void cancelar(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));

        if (!CANCELAVEIS.contains(pedido.getStatus())) {
            throw new StatusPedidoInvalidoException(pedidoId, pedido.getStatus(), "cancelado");
        }

        pedido.setStatus(StatusPedido.CANCELADO);
    }
}
