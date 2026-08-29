package br.com.unipds.jfood.administrativo.repository;

import br.com.unipds.jfood.administrativo.domain.Cupom;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CupomRepository extends JpaRepository<Cupom, Long> {

    /**
     * Cupom e o caso de contencao MAXIMA: 50 mil clientes disputando a mesma
     * linha no mesmo minuto. A escolha por pessimista esta justificada em
     * docs/aula05.md.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT c FROM Cupom c WHERE c.codigo = :codigo")
    Optional<Cupom> buscarPorCodigoParaAtualizacao(@Param("codigo") String codigo);
}
