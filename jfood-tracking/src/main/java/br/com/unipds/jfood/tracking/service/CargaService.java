package br.com.unipds.jfood.tracking.service;

import br.com.unipds.jfood.tracking.web.dto.PingRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * O teste de carga da Etapa 10.
 *
 * Ele exercita o MESMO caminho do endpoint de ingestao -- tres escritas por
 * ping, via Spring Data -- para que o numero medido seja o da aplicacao, e nao
 * o de um driver otimizado que ninguem usa em producao.
 */
@Service
public class CargaService {

    private static final Logger log = LoggerFactory.getLogger(CargaService.class);

    private final TrackingService trackingService;

    public CargaService(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    public Map<String, Object> disparar(int pontos, int entregadores, int concorrencia)
            throws InterruptedException {

        AtomicLong gravados = new AtomicLong();
        AtomicLong falhas = new AtomicLong();

        ExecutorService pool = Executors.newFixedThreadPool(concorrencia);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(concorrencia);

        int porThread = pontos / concorrencia;

        for (int t = 0; t < concorrencia; t++) {
            final int indiceDaThread = t;
            pool.submit(() -> {
                try {
                    largada.await();
                    for (int i = 0; i < porThread; i++) {
                        long entregadorId = 1000L + ((long) indiceDaThread * porThread + i) % entregadores;
                        try {
                            trackingService.registrarPing(pingSintetico(entregadorId, i));
                            gravados.incrementAndGet();
                        } catch (RuntimeException e) {
                            falhas.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    chegada.countDown();
                }
            });
        }

        long inicio = System.nanoTime();
        largada.countDown();
        boolean terminou = chegada.await(10, TimeUnit.MINUTES);
        Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);
        pool.shutdown();

        double pontosPorSegundo = gravados.get() / (duracao.toMillis() / 1000.0);
        // Cada ping sao TRES escritas
        double escritasPorSegundo = pontosPorSegundo * 3;

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("pontosGravados", gravados.get());
        resultado.put("falhas", falhas.get());
        resultado.put("entregadoresSimulados", entregadores);
        resultado.put("concorrencia", concorrencia);
        resultado.put("duracaoSegundos", Math.round(duracao.toMillis() / 100.0) / 10.0);
        resultado.put("pontosPorSegundo", Math.round(pontosPorSegundo));
        resultado.put("escritasPorSegundo", Math.round(escritasPorSegundo));
        resultado.put("terminouNoPrazo", terminou);

        log.info("Teste de carga: {}", resultado);
        return resultado;
    }

    private PingRequest pingSintetico(long entregadorId, int sequencia) {
        ThreadLocalRandom aleatorio = ThreadLocalRandom.current();
        return new PingRequest(
                entregadorId,
                9000L + entregadorId,
                -23.55 + aleatorio.nextDouble(-0.1, 0.1),
                -46.63 + aleatorio.nextDouble(-0.1, 0.1),
                aleatorio.nextDouble(0, 60),
                Instant.now().minusSeconds(5L * sequencia));
    }
}
