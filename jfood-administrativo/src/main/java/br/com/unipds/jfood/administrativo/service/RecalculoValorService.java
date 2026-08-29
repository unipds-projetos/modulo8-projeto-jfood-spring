package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A segunda forma de tratar o conflito otimista.
 *
 * No endpoint que o cliente chama, o certo e HTTP 409: ele esta na frente da
 * tela e pode recarregar. Aqui, num job de background, ninguem esta esperando --
 * entao a resposta certa e tentar de novo.
 */
@Service
public class RecalculoValorService {

    private static final Logger log = LoggerFactory.getLogger(RecalculoValorService.class);

    private final PedidoRepository pedidoRepository;

    public RecalculoValorService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    /**
     * O backoff exponencial (100 ms, 200 ms, 400 ms) existe para nao empilhar as
     * tentativas todas no mesmo instante -- e o mesmo raciocinio do jitter que
     * aparece no cache da Aula 9. O jitter de 50 ms termina de espalhar.
     *
     * Este @Retryable e o do Spring Framework 7 (org.springframework.resilience),
     * que nasceu no nucleo e dispensa a dependencia externa spring-retry. Ele e
     * ligado por @EnableResilientMethods na classe da aplicacao.
     *
     * ATENCAO: @Retryable tambem funciona por PROXY. Chamar este metodo de dentro
     * do proprio bean desliga o retry, exatamente como no @Transactional.
     */
    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = 3,
            delay = 100,
            multiplier = 2.0,
            jitter = 50)
    @Transactional
    public void recalcularValorTotal(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));

        pedido.getItens().size();          // forca o carregamento da colecao
        pedido.recalcularValorTotal();

        log.info("Pedido {} recalculado para {} (versao {})",
                pedidoId, pedido.getValorTotal(), pedido.getVersao());
    }
}
