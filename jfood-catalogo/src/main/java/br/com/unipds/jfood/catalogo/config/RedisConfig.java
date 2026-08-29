package br.com.unipds.jfood.catalogo.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
public class RedisConfig {

    /**
     * O ObjectMapper do cache NAO e um @Bean, e a razao vale a nota.
     *
     * O Spring Boot ja registra um ObjectMapper @Primary, usado para serializar as
     * respostas HTTP. Publicar um segundo faz o @Primary vencer a resolucao por
     * nome do parametro -- e o cache passa a receber o mapper do web, SEM o
     * default typing.
     *
     * O sintoma e cruel: a gravacao funciona e o JSON no Redis parece perfeito,
     * so que sem o carimbo de tipo. Na LEITURA, o Jackson devolve um LinkedHashMap
     * e o cache estoura com
     *
     *   ClassCastException: class java.util.LinkedHashMap cannot be cast to
     *   class br.com.unipds.jfood.catalogo.domain.Restaurante
     *
     * -- e so no HIT, nunca no MISS. Ou seja: passa no primeiro teste.
     *
     * E seria ruim ao contrario tambem: se este mapper virasse o do web, as
     * respostas da API sairiam com o nome das classes Java dentro do JSON.
     */
    private ObjectMapper objectMapperDoCache() {

        // A BLINDAGEM CONTRA RCE.
        //
        // Para saber se {"id":1,"nome":"X"} e um Restaurante ou um PageImpl, o
        // Jackson precisa do default typing, que grava o nome da classe DENTRO do
        // JSON: ["br.com...Restaurante", {"id":1}].
        //
        // Confiar cegamente num nome de classe vindo de texto externo e uma das
        // maiores falhas do Java moderno: historicamente, atacantes enviavam JSON
        // com o nome de uma classe interna que executava comandos no servidor.
        // Desde o Jackson 2.10 a unica forma nao-deprecated EXIGE um validador --
        // sem ele, o codigo nem compila.
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("java.util")
                .allowIfSubType("java.math")
                .allowIfSubType("java.time")
                .allowIfSubType("org.springframework.data.domain")
                .allowIfSubType("br.com.unipds.jfood.catalogo.domain")
                .build();

        return JsonMapper.builder()
                .findAndAddModules()
                // Datas em ISO-8601, e nao como numero. Legivel por humanos e por
                // qualquer linguagem.
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                // NON_FINAL_AND_RECORDS, e nao NON_FINAL: records TAMBEM sao final,
                // e sem o _AND_RECORDS as entidades do JFood -- que sao todas
                // records -- perderiam o carimbo de tipo e nao voltariam do cache.
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL_AND_RECORDS)
                .build();
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // Rede de seguranca: se alguem esquecer de invalidar, o dado morre
                // sozinho em 60 minutos. A inconsistencia passa a ter prazo.
                .entryTtl(Duration.ofMinutes(60))
                // Nao gasta RAM guardando nulo
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJacksonJsonRedisSerializer(objectMapperDoCache())));
    }

    /**
     * O Spring Boot autoconfigura RedisTemplate<Object, Object> com serializacao
     * JDK -- o "lixo binario" da secao 9.7 da apostila. Este bean troca a
     * serializacao pela mesma JSON do cache e fixa a chave como String.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        var chaves = new StringRedisSerializer();
        var valores = new GenericJacksonJsonRedisSerializer(objectMapperDoCache());

        template.setKeySerializer(chaves);
        template.setHashKeySerializer(chaves);
        template.setValueSerializer(valores);
        template.setHashValueSerializer(valores);
        return template;
    }
}
