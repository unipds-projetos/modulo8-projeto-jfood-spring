package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.AuditoriaPedido;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaPedidoRepository extends JpaRepository<AuditoriaPedido, Long> {

    List<AuditoriaPedido> findByPedidoIdOrderByAlteradoEmDesc(Long pedidoId);
}
