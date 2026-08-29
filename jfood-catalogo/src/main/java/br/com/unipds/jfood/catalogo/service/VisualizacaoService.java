package br.com.unipds.jfood.catalogo.service;

import br.com.unipds.jfood.catalogo.domain.Restaurante;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * WRITE BEHIND no contador de visualizacoes.
 *
 * Cada abertura da tela do restaurante incrementa um contador. Gravar isso no
 * MongoDB a cada acesso seria um UPDATE por request na tela mais aberta do app --
 * e o cache do detalhe deixaria de servir para alguma coisa, porque a escrita
 * continuaria indo ao banco de qualquer jeito.
 *
 * O Redis absorve os incrementos (INCR e atomico e custa microssegundos) e um job
 * consolida a cada 5 minutos, com UM update por restaurante.
 */
@Service
public class VisualizacaoService {

    private static final Logger log = LoggerFactory.getLogger(VisualizacaoService.class);
    private static final String PREFIXO = "visualizacoes:";

    private final StringRedisTemplate redis;
    private final MongoTemplate mongoTemplate;

    public VisualizacaoService(StringRedisTemplate redis, MongoTemplate mongoTemplate) {
        this.redis = redis;
        this.mongoTemplate = mongoTemplate;
    }

    public Long registrarVisualizacao(String restauranteId) {
        return redis.opsForValue().increment(PREFIXO + restauranteId);
    }

    public long pendentes(String restauranteId) {
        String valor = redis.opsForValue().get(PREFIXO + restauranteId);
        return valor == null ? 0 : Long.parseLong(valor);
    }

    /**
     * Consolida os contadores no MongoDB.
     *
     * O GETDEL le e apaga em uma operacao atomica: sem isso, um incremento que
     * chegasse entre o GET e o DEL seria perdido silenciosamente.
     *
     * Ainda assim, a janela nao fecha 100%: se o Redis morrer entre dois flushes,
     * os incrementos daquele intervalo se perdem. Isso e ACEITAVEL aqui e nao
     * seria no valor_total de um pedido -- o porque esta em docs/aula09.md.
     */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public long consolidar() {
        Set<String> chaves = redis.keys(PREFIXO + "*");
        if (chaves == null || chaves.isEmpty()) {
            return 0;
        }

        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Restaurante.class);
        long total = 0;

        for (String chave : chaves) {
            String valor = redis.opsForValue().getAndDelete(chave);
            if (valor == null) {
                continue;
            }
            long incremento = Long.parseLong(valor);
            String id = chave.substring(PREFIXO.length());

            bulk.updateOne(Query.query(Criteria.where("_id").is(id)),
                           new Update().inc("visualizacoes", incremento));
            total += incremento;
        }

        if (total > 0) {
            bulk.execute();                                  // UM round-trip
            log.info("Write-behind: {} visualizacoes consolidadas em {} restaurantes",
                    total, chaves.size());
        }
        return total;
    }
}
