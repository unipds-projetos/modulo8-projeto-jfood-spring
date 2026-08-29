package br.com.unipds.jfood.catalogo.service;

import br.com.unipds.jfood.catalogo.domain.Restaurante;
import br.com.unipds.jfood.catalogo.repository.RestauranteRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * A chave "restaurantes em destaque" -- e o experimento de CACHE STAMPEDE.
 *
 * O cache aqui e feito na mao, e nao com @Cacheable, porque o experimento precisa
 * controlar o TTL e contar os acessos ao banco.
 */
@Service
public class DestaqueService {

    private static final Logger log = LoggerFactory.getLogger(DestaqueService.class);
    private static final String CHAVE = "destaques";

    private final RestauranteRepository restauranteRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration ttl;
    private final Duration jitterMaximo;

    /** Conta as idas ao MongoDB. E o numero que o experimento entrega. */
    private final AtomicLong acessosAoBanco = new AtomicLong();

    public DestaqueService(RestauranteRepository restauranteRepository,
                           RedisTemplate<String, Object> redisTemplate,
                           @Value("${jfood.cache.destaques-ttl}") Duration ttl,
                           @Value("${jfood.cache.destaques-jitter}") Duration jitterMaximo) {
        this.restauranteRepository = restauranteRepository;
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
        this.jitterMaximo = jitterMaximo;
    }

    /**
     * SEM jitter: TTL fixo.
     *
     * Todas as replicas gravaram a chave no mesmo instante, entao ela expira para
     * todo mundo no mesmo instante -- e as N requisicoes que chegarem naquele
     * milissegundo vao TODAS ao banco. E o thundering herd.
     */
    @SuppressWarnings("unchecked")
    public List<Restaurante> destaquesSemJitter() {
        Object cacheado = redisTemplate.opsForValue().get(CHAVE);
        if (cacheado != null) {
            return (List<Restaurante>) cacheado;
        }
        List<Restaurante> destaques = buscarNoBanco();
        redisTemplate.opsForValue().set(CHAVE, destaques, ttl);
        return destaques;
    }

    /**
     * COM jitter: TTL + um intervalo aleatorio.
     *
     * O objetivo nao e alongar o cache -- e DESSINCRONIZAR as expiracoes. Com
     * 60 s + ate 15 s de jitter, as chaves das varias replicas passam a expirar
     * espalhadas por uma janela de 15 s, e a cada instante so uma fracao das
     * requisicoes encontra miss.
     */
    @SuppressWarnings("unchecked")
    public List<Restaurante> destaquesComJitter() {
        Object cacheado = redisTemplate.opsForValue().get(CHAVE);
        if (cacheado != null) {
            return (List<Restaurante>) cacheado;
        }
        List<Restaurante> destaques = buscarNoBanco();
        Duration jitter = Duration.ofMillis(
                ThreadLocalRandom.current().nextLong(jitterMaximo.toMillis() + 1));
        redisTemplate.opsForValue().set(CHAVE, destaques, ttl.plus(jitter));
        return destaques;
    }

    /**
     * COM TRAVA DE RECALCULO (single flight).
     *
     * E o que realmente resolve o herd em UMA chave: no miss, cada requisicao
     * tenta um SET NX de uma chave de trava. So a primeira consegue e vai ao
     * banco; as outras esperam alguns milissegundos e releem o cache.
     *
     * A trava tem TTL proprio (5 s): se o processo que a pegou morrer no meio, a
     * trava expira sozinha em vez de bloquear o cache para sempre.
     */
    @SuppressWarnings("unchecked")
    public List<Restaurante> destaquesComTrava() {
        Object cacheado = redisTemplate.opsForValue().get(CHAVE);
        if (cacheado != null) {
            return (List<Restaurante>) cacheado;
        }

        Boolean peguei = redisTemplate.opsForValue()
                .setIfAbsent(CHAVE + ":trava", "1", Duration.ofSeconds(5));

        if (Boolean.TRUE.equals(peguei)) {
            try {
                List<Restaurante> destaques = buscarNoBanco();
                redisTemplate.opsForValue().set(CHAVE, destaques, ttl);
                return destaques;
            } finally {
                redisTemplate.delete(CHAVE + ":trava");
            }
        }

        // Perdeu a corrida: espera o vencedor gravar e rele.
        for (int tentativa = 0; tentativa < 50; tentativa++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            cacheado = redisTemplate.opsForValue().get(CHAVE);
            if (cacheado != null) {
                return (List<Restaurante>) cacheado;
            }
        }
        // Ultimo recurso: o vencedor demorou demais. Melhor uma consulta a mais
        // do que uma requisicao sem resposta.
        return buscarNoBanco();
    }

    private List<Restaurante> buscarNoBanco() {
        long n = acessosAoBanco.incrementAndGet();
        log.info("MISS -> acesso #{} ao MongoDB para montar os destaques", n);
        return restauranteRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "nota_media")))
                .getContent();
    }

    public long acessosAoBanco() {
        return acessosAoBanco.get();
    }

    public void reiniciarExperimento() {
        acessosAoBanco.set(0);
        redisTemplate.delete(CHAVE);
        redisTemplate.delete(CHAVE + ":trava");
    }
}
