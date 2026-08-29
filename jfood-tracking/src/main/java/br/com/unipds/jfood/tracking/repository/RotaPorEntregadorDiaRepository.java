package br.com.unipds.jfood.tracking.repository;

import br.com.unipds.jfood.tracking.domain.RotaPorEntregadorDia;
import br.com.unipds.jfood.tracking.domain.RotaPorEntregadorDiaChave;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RotaPorEntregadorDiaRepository
        extends CassandraRepository<RotaPorEntregadorDia, RotaPorEntregadorDiaChave> {

    /**
     * UMA particao. A consulta informa a chave de particao inteira
     * (entregador_id + dia) e o coordenador vai direto ao no que tem o dado.
     *
     * Sem ORDER BY: o CLUSTERING ORDER BY (instante DESC) da tabela ja gravou o
     * disco na ordem certa.
     */
    @Query("SELECT * FROM rota_por_entregador_dia WHERE entregador_id = :entregadorId AND dia = :dia")
    List<RotaPorEntregadorDia> buscarRotaDoDia(@Param("entregadorId") Long entregadorId,
                                               @Param("dia") LocalDate dia);

    @Query("SELECT * FROM rota_por_entregador_dia WHERE entregador_id = :entregadorId AND dia = :dia LIMIT :limite")
    List<RotaPorEntregadorDia> buscarUltimosPontosDoDia(@Param("entregadorId") Long entregadorId,
                                                        @Param("dia") LocalDate dia,
                                                        @Param("limite") int limite);
}
