package br.com.unipds.jfood.administrativo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cupom")
public class Cupom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "valor_desconto", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorDesconto;

    @Column(name = "quantidade_total", nullable = false)
    private Integer quantidadeTotal;

    @Column(name = "quantidade_resgatada", nullable = false)
    private Integer quantidadeResgatada = 0;

    @Column(name = "valido_ate", nullable = false)
    private OffsetDateTime validoAte;

    public boolean temEstoque() {
        return quantidadeResgatada < quantidadeTotal;
    }

    public boolean estaValido() {
        return validoAte.isAfter(OffsetDateTime.now());
    }

    public void resgatar() {
        this.quantidadeResgatada = this.quantidadeResgatada + 1;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }

    public Integer getQuantidadeTotal() { return quantidadeTotal; }
    public void setQuantidadeTotal(Integer quantidadeTotal) { this.quantidadeTotal = quantidadeTotal; }

    public Integer getQuantidadeResgatada() { return quantidadeResgatada; }
    public void setQuantidadeResgatada(Integer quantidadeResgatada) { this.quantidadeResgatada = quantidadeResgatada; }

    public OffsetDateTime getValidoAte() { return validoAte; }
    public void setValidoAte(OffsetDateTime validoAte) { this.validoAte = validoAte; }
}
