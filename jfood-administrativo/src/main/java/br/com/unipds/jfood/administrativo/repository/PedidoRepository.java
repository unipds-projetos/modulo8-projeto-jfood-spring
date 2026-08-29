package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.Pedido;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Derived query: o Spring le o nome do metodo e monta a consulta.
     * Traz SO os pedidos -- restaurante e entregador ficam para o lazy loading.
     */
    List<Pedido> findByClienteIdOrderByDataPedidoDesc(Long clienteId);

    /**
     * A mesma consulta, em UMA query.
     *
     * O left join fetch e obrigatorio no entregador: pedido ainda nao despachado
     * tem entregador_id nulo, e um inner join fetch faria esses pedidos sumirem
     * do historico -- um bug pior que o N+1, porque nao aparece no log.
     */
    @Query("""
           SELECT p FROM Pedido p
             JOIN FETCH p.restaurante
             LEFT JOIN FETCH p.entregador
            WHERE p.cliente.id = :clienteId
            ORDER BY p.dataPedido DESC
           """)
    List<Pedido> buscarHistoricoComRestauranteEEntregador(@Param("clienteId") Long clienteId);
}
