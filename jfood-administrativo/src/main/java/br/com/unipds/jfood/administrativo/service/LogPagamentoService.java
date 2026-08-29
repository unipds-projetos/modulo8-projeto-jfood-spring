package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.LogPagamento;
import br.com.unipds.jfood.administrativo.repository.LogPagamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogPagamentoService {

    private final LogPagamentoRepository logRepository;

    public LogPagamentoService(LogPagamentoRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * Transacao PROPRIA: commita independentemente do que acontece fora.
     *
     * Sem REQUIRES_NEW, o rollback da confirmacao levaria o log junto -- e a
     * rastreabilidade do incidente se perderia justamente no caso em que ela e
     * necessaria.
     *
     * CUIDADO: REQUIRES_NEW consome uma SEGUNDA conexao do pool enquanto a
     * primeira fica suspensa. Usar isto dentro de um laco sobre muitos itens
     * esgota o pool e trava a aplicacao.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFalha(Long pedidoId, String motivo) {
        logRepository.save(new LogPagamento(pedidoId, motivo));
        // COMMIT aqui, antes de voltar para quem chamou
    }
}
