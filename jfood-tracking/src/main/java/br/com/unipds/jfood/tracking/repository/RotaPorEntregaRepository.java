package br.com.unipds.jfood.tracking.repository;

import br.com.unipds.jfood.tracking.domain.RotaPorEntrega;
import br.com.unipds.jfood.tracking.domain.RotaPorEntregaChave;
import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RotaPorEntregaRepository
        extends CassandraRepository<RotaPorEntrega, RotaPorEntregaChave> {

    @Query("SELECT * FROM rota_por_entrega WHERE entrega_id = :entregaId")
    List<RotaPorEntrega> buscarRotaDaEntrega(@Param("entregaId") Long entregaId);
}
