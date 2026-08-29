package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.repository.PedidoRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O servico de despacho roda em varias instancias ao mesmo tempo.
 * Cada uma pega o proprio lote com FOR UPDATE SKIP LOCKED e nenhuma espera pela
 * outra -- e nenhum pedido e despachado duas vezes.
 */
@Service
public class DespachoService {

    private static final Logger log = LoggerFactory.getLogger(DespachoService.class);
    private static final int TAMANHO_DO_LOTE = 20;

    private final PedidoRepository pedidoRepository;

    public DespachoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional
    public List<Long> despacharLote() {
        List<Pedido> lote = pedidoRepository.buscarLoteParaDespacho(TAMANHO_DO_LOTE);

        OffsetDateTime agora = OffsetDateTime.now();
        lote.forEach(pedido -> pedido.setDespachadoEm(agora));

        List<Long> ids = lote.stream().map(Pedido::getId).toList();
        log.info("Lote despachado: {}", ids);
        return ids;
        // O COMMIT libera os locks; os pedidos ja estao com despachado_em preenchido,
        // entao a proxima rodada nao os encontra mais.
    }
}
