package br.com.unipds.jfood.administrativo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Somente leitura pela aplicacao. Quem escreve aqui e o trigger -- e o banco
 * revoga UPDATE e DELETE de PUBLIC, entao nem sem querer da para editar.
 */
@Entity
@Table(name = "auditoria_pedido")
public class AuditoriaPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "status_antigo", length = 20)
    private String statusAntigo;

    @Column(name = "status_novo", nullable = false, length = 20)
    private String statusNovo;

    @Column(name = "alterado_por", nullable = false)
    private String alteradoPor;

    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public String getStatusAntigo() { return statusAntigo; }
    public String getStatusNovo() { return statusNovo; }
    public String getAlteradoPor() { return alteradoPor; }
    public OffsetDateTime getAlteradoEm() { return alteradoEm; }
}
