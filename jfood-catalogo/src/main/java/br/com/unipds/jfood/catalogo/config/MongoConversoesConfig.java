package br.com.unipds.jfood.catalogo.config;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * O BSON nao tem tipo "hora do dia".
 *
 * Ele tem Date (um instante) e Timestamp (interno do replica set) -- e nenhum
 * dos dois serve para "abre as 18:00", que nao e um instante, e sim um horario
 * que se repete todo dia. Guardar isso como Date obrigaria a inventar uma data
 * de referencia e a lidar com fuso onde nao ha fuso nenhum.
 *
 * A solucao e guardar "18:00" como string ISO e converter nas duas pontas. O
 * Java continua com LocalTime, que e o tipo certo; o documento continua legivel
 * no mongosh.
 *
 * Sem isto, a leitura falha com:
 *   ConverterNotFoundException: No converter found capable of converting from
 *   type [java.lang.String] to type [java.time.LocalTime]
 */
@Configuration
public class MongoConversoesConfig {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new LocalTimeParaString(),
                new StringParaLocalTime()));
    }

    @WritingConverter
    static class LocalTimeParaString implements Converter<LocalTime, String> {
        @Override
        public String convert(LocalTime origem) {
            return origem.format(HORA);
        }
    }

    @ReadingConverter
    static class StringParaLocalTime implements Converter<String, LocalTime> {
        @Override
        public LocalTime convert(String origem) {
            return LocalTime.parse(origem, HORA);
        }
    }
}
