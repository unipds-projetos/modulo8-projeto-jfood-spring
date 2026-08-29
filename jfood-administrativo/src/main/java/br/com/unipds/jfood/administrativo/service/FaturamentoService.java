package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.repository.FaturamentoRepository;
import br.com.unipds.jfood.administrativo.repository.projection.FaturamentoRestaurante;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaturamentoService {

    private static final Logger log = LoggerFactory.getLogger(FaturamentoService.class);

    private final FaturamentoRepository faturamentoRepository;
    private final DataSource dataSource;

    public FaturamentoService(FaturamentoRepository faturamentoRepository, DataSource dataSource) {
        this.faturamentoRepository = faturamentoRepository;
        this.dataSource = dataSource;
    }

    @Transactional(readOnly = true)
    public List<FaturamentoRestaurante> lerSnapshot() {
        return faturamentoRepository.lerSnapshot();
    }

    /**
     * REFRESH ... CONCURRENTLY nao pode rodar dentro de um bloco de transacao --
     * o PostgreSQL recusa com
     *
     *   REFRESH MATERIALIZED VIEW CONCURRENTLY cannot run inside a transaction block
     *
     * Por isso este metodo NAO leva @Transactional e vai direto na conexao, em
     * autocommit. E o detalhe que faz muita gente desistir do CONCURRENTLY e
     * voltar para o REFRESH que trava as leituras.
     */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    public void atualizarSnapshot() {
        long inicio = System.nanoTime();
        try (Connection conexao = dataSource.getConnection()) {
            boolean autocommitOriginal = conexao.getAutoCommit();
            conexao.setAutoCommit(true);
            try (Statement statement = conexao.createStatement()) {
                statement.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_faturamento_por_restaurante");
            } finally {
                conexao.setAutoCommit(autocommitOriginal);
            }
        } catch (SQLException e) {
            log.error("Falha ao atualizar mv_faturamento_por_restaurante", e);
            throw new IllegalStateException("Falha no refresh da materialized view", e);
        }
        log.info("mv_faturamento_por_restaurante atualizada em {} ms",
                (System.nanoTime() - inicio) / 1_000_000);
    }
}
