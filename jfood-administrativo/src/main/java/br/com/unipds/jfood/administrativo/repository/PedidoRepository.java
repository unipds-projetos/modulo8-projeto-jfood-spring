package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Derived query: o Spring le o nome do metodo e monta a consulta.
     * Traz SO os pedidos -- restaurante e entregador ficam para o lazy loading.
     */
    List<Pedido> findByClienteIdOrderByDataPedidoDesc(Long clienteId);
}
