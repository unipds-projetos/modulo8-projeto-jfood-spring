package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.LogPagamento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogPagamentoRepository extends JpaRepository<LogPagamento, Long> {

    List<LogPagamento> findByPedidoIdOrderByOcorridoEmDesc(Long pedidoId);
}
