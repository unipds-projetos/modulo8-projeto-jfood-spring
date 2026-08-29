package br.com.unipds.jfood.administrativo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import br.com.unipds.jfood.administrativo.domain.StatusPedido;
import br.com.unipds.jfood.administrativo.repository.PagamentoRepository;
import br.com.unipds.jfood.administrativo.repository.PedidoRepository;
import br.com.unipds.jfood.administrativo.service.ConfirmacaoPedidoService;
import br.com.unipds.jfood.administrativo.service.gateway.GatewayPagamentoSimulado;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * O criterio de pronto da Etapa 5.
 *
 * Precisa do PostgreSQL de pe (docker compose up -d): o lock pessimista e uma
 * caracteristica do banco, e testa-lo contra um H2 em memoria testaria outra
 * coisa.
 */
@SpringBootTest
@DisplayName("Confirmacoes concorrentes do mesmo pedido")
class ConfirmacaoConcorrenteTest {

    private static final int THREADS = 8;

    @Autowired private ConfirmacaoPedidoService confirmacaoService;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private PagamentoRepository pagamentoRepository;
    @Autowired private GatewayPagamentoSimulado gateway;

    private Long pedidoId;

    @BeforeEach
    void prepararPedidoEmAberto() {
        gateway.limparRecusas();

        pedidoId = pedidoRepository.findByStatusOrderByDataPedidoDesc(StatusPedido.CRIADO)
                .stream()
                .map(Pedido::getId)
                .findFirst()
                .orElseGet(this::reabrirUmPedido);

        pagamentoRepository.findByPedidoId(pedidoId).ifPresent(pagamentoRepository::delete);
    }

    private Long reabrirUmPedido() {
        Pedido pedido = pedidoRepository.findAll().getFirst();
        pagamentoRepository.findByPedidoId(pedido.getId()).ifPresent(pagamentoRepository::delete);
        pedido.setStatus(StatusPedido.CRIADO);
        return pedidoRepository.save(pedido).getId();
    }

    @Test
    @DisplayName("terminam com exatamente um pagamento registrado")
    void apenasUmaConfirmacaoVence() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(THREADS);

        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger recusas = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    largada.await();                      // todas partem juntas
                    confirmacaoService.confirmarPedido(pedidoId);
                    sucessos.incrementAndGet();
                } catch (Exception e) {
                    recusas.incrementAndGet();            // StatusPedidoInvalido, ou timeout de lock
                } finally {
                    chegada.countDown();
                }
            });
        }

        largada.countDown();
        assertThat(chegada.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(sucessos.get())
                .as("apenas uma thread pode confirmar")
                .isEqualTo(1);
        assertThat(recusas.get()).isEqualTo(THREADS - 1);
        assertThat(pagamentoRepository.countByPedidoId(pedidoId))
                .as("exatamente um pagamento registrado")
                .isEqualTo(1);
        assertThat(pedidoRepository.findById(pedidoId).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.CONFIRMADO);
    }
}
