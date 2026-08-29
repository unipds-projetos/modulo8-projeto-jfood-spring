package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.CupomResgate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CupomResgateRepository extends JpaRepository<CupomResgate, Long> {

    boolean existsByCupomIdAndClienteId(Long cupomId, Long clienteId);
}
