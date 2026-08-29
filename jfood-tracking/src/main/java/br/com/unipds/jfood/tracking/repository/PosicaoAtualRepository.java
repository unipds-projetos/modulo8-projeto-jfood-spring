package br.com.unipds.jfood.tracking.repository;

import br.com.unipds.jfood.tracking.domain.PosicaoAtualEntregador;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface PosicaoAtualRepository
        extends CassandraRepository<PosicaoAtualEntregador, Long> {
}
