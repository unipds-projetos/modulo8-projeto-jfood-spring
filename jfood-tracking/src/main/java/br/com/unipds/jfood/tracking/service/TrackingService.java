package br.com.unipds.jfood.tracking.service;

import br.com.unipds.jfood.tracking.domain.PosicaoAtualEntregador;
import br.com.unipds.jfood.tracking.domain.RotaPorEntrega;
import br.com.unipds.jfood.tracking.domain.RotaPorEntregaChave;
import br.com.unipds.jfood.tracking.domain.RotaPorEntregadorDia;
import br.com.unipds.jfood.tracking.domain.RotaPorEntregadorDiaChave;
import br.com.unipds.jfood.tracking.repository.PosicaoAtualRepository;
import br.com.unipds.jfood.tracking.repository.RotaPorEntregaRepository;
import br.com.unipds.jfood.tracking.repository.RotaPorEntregadorDiaRepository;
import br.com.unipds.jfood.tracking.web.dto.PingRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.stereotype.Service;

@Service
public class TrackingService {

    /**
     * TTL EM VEZ DE DELETE.
     *
     * A rota bruta so interessa por 90 dias. A tentacao e um job noturno com
     * DELETE FROM ... WHERE dia < ? -- e ela e uma armadilha.
     *
     * No Cassandra, DELETE nao apaga: ele ESCREVE um marcador (tombstone) que
     * diz "isto foi apagado". O dado so some de verdade na proxima compaction,
     * depois de gc_grace_seconds (10 dias por padrao). Um DELETE em massa de
     * centenas de milhoes de linhas grava centenas de milhoes de tombstones --
     * que sao lidos em TODA consulta a particao afetada, ate a compaction rodar.
     * O sintoma e uma leitura que fica cada vez mais lenta ate estourar
     * tombstone_failure_threshold e a consulta simplesmente parar de responder.
     *
     * Com USING TTL, cada celula ja nasce com prazo. Quando ela vence, vira
     * tombstone da MESMA forma -- mas espalhado no tempo e por particao, e nao
     * concentrado em uma tempestade noturna.
     */
    private static final Duration RETENCAO = Duration.ofDays(90);

    private final CassandraTemplate cassandraTemplate;
    private final RotaPorEntregadorDiaRepository rotaPorDiaRepository;
    private final RotaPorEntregaRepository rotaPorEntregaRepository;
    private final PosicaoAtualRepository posicaoAtualRepository;

    public TrackingService(CassandraTemplate cassandraTemplate,
                           RotaPorEntregadorDiaRepository rotaPorDiaRepository,
                           RotaPorEntregaRepository rotaPorEntregaRepository,
                           PosicaoAtualRepository posicaoAtualRepository) {
        this.cassandraTemplate = cassandraTemplate;
        this.rotaPorDiaRepository = rotaPorDiaRepository;
        this.rotaPorEntregaRepository = rotaPorEntregaRepository;
        this.posicaoAtualRepository = posicaoAtualRepository;
    }

    /**
     * Um ping vira TRES escritas -- uma por pergunta que o sistema precisa
     * responder. Isso e modelagem Query-First, e nao desperdicio: no Cassandra a
     * escrita e barata (vai para o commitlog e a memtable, sequencialmente) e a
     * leitura de uma particao errada e cara ou impossivel.
     */
    public void registrarPing(PingRequest ping) {
        Instant instante = ping.instanteOuAgora();
        LocalDate dia = instante.atZone(ZoneOffset.UTC).toLocalDate();

        InsertOptions comTtl = InsertOptions.builder().ttl(RETENCAO).build();

        cassandraTemplate.insert(new RotaPorEntregadorDia(
                new RotaPorEntregadorDiaChave(ping.entregadorId(), dia, instante),
                ping.latitude(), ping.longitude(), ping.velocidadeKmh(), ping.entregaId()), comTtl);

        cassandraTemplate.insert(new RotaPorEntrega(
                new RotaPorEntregaChave(ping.entregaId(), instante),
                ping.entregadorId(), ping.latitude(), ping.longitude(), ping.velocidadeKmh()),
                comTtl);

        // A posicao atual NAO leva TTL: ela e sobrescrita a cada ping (upsert), e
        // um TTL aqui faria o entregador "sumir do mapa" 90 dias depois de parar.
        posicaoAtualRepository.save(new PosicaoAtualEntregador(
                ping.entregadorId(), instante, ping.latitude(), ping.longitude(),
                ping.velocidadeKmh(), ping.entregaId()));
    }

    /** Pergunta 1 — "onde esta o entregador X agora?" Uma linha, uma particao. */
    public PosicaoAtualEntregador posicaoAtual(Long entregadorId) {
        return posicaoAtualRepository.findById(entregadorId).orElse(null);
    }

    /** Pergunta 2 — "qual foi a rota completa da entrega Y?" Uma particao. */
    public List<RotaPorEntrega> rotaDaEntrega(Long entregaId) {
        return rotaPorEntregaRepository.buscarRotaDaEntrega(entregaId);
    }

    /** Pergunta 3 — "por onde o entregador X passou no dia D?" Uma particao. */
    public List<RotaPorEntregadorDia> rotaDoDia(Long entregadorId, LocalDate dia) {
        return rotaPorDiaRepository.buscarRotaDoDia(entregadorId, dia);
    }

    /**
     * "Ultimos 7 dias" -- o CUSTO do bucketing.
     *
     * Com a particao por (entregador_id, dia), esta pergunta deixa de ser UMA
     * leitura e passa a ser SETE, montadas pela aplicacao. E a troca que se
     * aceita: sete leituras rapidas e previsiveis contra uma leitura de uma
     * particao que cresce para sempre.
     *
     * O que NAO se deve fazer e um IN com sete dias em uma consulta so: o
     * coordenador teria de falar com varios nos e esperar o mais lento -- o
     * scatter-gather que o Cassandra existe para evitar.
     */
    public List<RotaPorEntregadorDia> rotaDosUltimosDias(Long entregadorId, int dias) {
        LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
        return java.util.stream.IntStream.range(0, dias)
                .mapToObj(hoje::minusDays)
                .flatMap(dia -> rotaPorDiaRepository.buscarRotaDoDia(entregadorId, dia).stream())
                .toList();
    }
}
