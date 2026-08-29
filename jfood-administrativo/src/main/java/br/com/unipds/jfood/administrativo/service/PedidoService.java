package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.repository.PedidoRepository;
import br.com.unipds.jfood.administrativo.web.dto.PedidoResumoResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    /**
     * O N+1, de proposito.
     *
     * A consulta traz os pedidos em UMA query. Cada getRestaurante().getNome() e
     * cada getEntregador().getNome() dentro do laco dispara outra -- o proxy lazy
     * so vai ao banco quando alguem chama um getter dele.
     *
     * O numero medido esta em docs/aula03.md. A correcao vem em
     * listarPedidosDoClienteComFetch.
     */
    @Transactional(readOnly = true)
    public List<PedidoResumoResponse> listarPedidosDoCliente(Long clienteId) {
        List<Pedido> pedidos = pedidoRepository.findByClienteIdOrderByDataPedidoDesc(clienteId);

        List<PedidoResumoResponse> resposta = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            resposta.add(new PedidoResumoResponse(
                    pedido.getId(),
                    pedido.getRestaurante().getNome(),                       // +1 query
                    pedido.getEntregador() == null
                            ? null
                            : pedido.getEntregador().getNome(),              // +1 query
                    pedido.getStatus(),
                    pedido.getDataPedido(),
                    pedido.getValorTotal()));
        }
        return resposta;
    }

    /**
     * A mesma tela, corrigida: UMA query.
     *
     * O mapeamento continua LAZY -- trocar para EAGER resolveria aqui e criaria o
     * problema em toda outra consulta a Pedido. LAZY no mapeamento + JOIN FETCH na
     * consulta que precisa e a combinacao certa.
     */
    @Transactional(readOnly = true)
    public List<PedidoResumoResponse> listarPedidosDoClienteComFetch(Long clienteId) {
        return pedidoRepository.buscarHistoricoComRestauranteEEntregador(clienteId)
                .stream()
                .map(pedido -> new PedidoResumoResponse(
                        pedido.getId(),
                        pedido.getRestaurante().getNome(),
                        pedido.getEntregador() == null
                                ? null
                                : pedido.getEntregador().getNome(),
                        pedido.getStatus(),
                        pedido.getDataPedido(),
                        pedido.getValorTotal()))
                .toList();
    }

    /**
     * Remove um item do pedido tirando-o da COLECAO -- sem chamar delete() em
     * lugar nenhum.
     *
     * Rodado ANTES de declarar orphanRemoval, isto deixava a linha de item_pedido
     * no banco: remover da lista em memoria nao remove do banco, e o
     * CascadeType.REMOVE tambem nao ajuda -- ele age quando o PAI e deletado, e o
     * pai aqui esta vivo. Pior: o valor_total ja tinha sido recalculado, e o
     * pedido passava a valer menos do que a soma dos seus proprios itens.
     *
     * Com orphanRemoval = true declarado em Pedido.itens, o Hibernate emite o
     * DELETE FROM item_pedido WHERE id = ? ao fechar a transacao -- sem nenhuma
     * chamada explicita a delete(). Os dois numeros estao em docs/aula03.md.
     */
    @Transactional
    public void removerItem(Long pedidoId, Long itemPedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));

        pedido.getItens().removeIf(item -> item.getId().equals(itemPedidoId));
        pedido.recalcularValorTotal();
        // dirty checking grava o novo valor_total ao fechar a transacao
    }
}
