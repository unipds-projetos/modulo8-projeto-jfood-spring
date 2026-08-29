package br.com.unipds.jfood.catalogo.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * O disparo das requisicoes simultaneas, feito DENTRO da JVM.
 *
 * Fazer isso com `xargs -P 200 curl` nao mede o que se quer medir: os 200
 * processos nao partem juntos -- o primeiro ja gravou o cache antes de o
 * quinquagesimo ter subido -- e o resultado varia de 2 a 25 acessos entre
 * rodadas, sem relacao com a estrategia que se esta testando.
 *
 * Com uma CountDownLatch, as N threads chegam ao cache no MESMO instante, e o
 * numero passa a significar alguma coisa.
 */
@Service
public class ExperimentoStampedeService {

    public enum Estrategia { SEM_JITTER, COM_JITTER, COM_TRAVA }

    private final DestaqueService destaqueService;

    public ExperimentoStampedeService(DestaqueService destaqueService) {
        this.destaqueService = destaqueService;
    }

    public Map<String, Object> rodar(Estrategia estrategia, int requisicoes) throws InterruptedException {
        destaqueService.reiniciarExperimento();      // zera o contador e expira a chave

        Supplier<?> chamada = switch (estrategia) {
            case SEM_JITTER -> destaqueService::destaquesSemJitter;
            case COM_JITTER -> destaqueService::destaquesComJitter;
            case COM_TRAVA  -> destaqueService::destaquesComTrava;
        };

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(requisicoes);

        for (int i = 0; i < requisicoes; i++) {
            pool.submit(() -> {
                try {
                    largada.await();                  // todas partem juntas
                    chamada.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    chegada.countDown();
                }
            });
        }

        long inicio = System.nanoTime();
        largada.countDown();
        boolean terminou = chegada.await(60, TimeUnit.SECONDS);
        Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);
        pool.shutdown();

        return Map.of(
                "estrategia", estrategia.name(),
                "requisicoes", requisicoes,
                "acessosAoBanco", destaqueService.acessosAoBanco(),
                "duracaoMs", duracao.toMillis(),
                "terminouNoPrazo", terminou);
    }
}
