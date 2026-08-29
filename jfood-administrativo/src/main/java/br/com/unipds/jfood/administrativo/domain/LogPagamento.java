package br.com.unipds.jfood.administrativo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Sem relacionamento com Pedido, so o id solto -- de proposito.
 * A trilha registra TENTATIVAS, e precisa sobreviver ao rollback da transacao
 * que a originou. Uma FK a tornaria refem da integridade referencial daquilo
 * que ela existe para auditar.
 */
@Entity
@Table(name = "log_pagamento")
public class LogPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(nullable = false, length = 255)
    private String motivo;

    @Column(name = "ocorrido_em", nullable = false)
    private OffsetDateTime ocorridoEm = OffsetDateTime.now();

    public LogPagamento() {
    }

    public LogPagamento(Long pedidoId, String motivo) {
        this.pedidoId = pedidoId;
        this.motivo = motivo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public OffsetDateTime getOcorridoEm() { return ocorridoEm; }
    public void setOcorridoEm(OffsetDateTime ocorridoEm) { this.ocorridoEm = ocorridoEm; }
}
