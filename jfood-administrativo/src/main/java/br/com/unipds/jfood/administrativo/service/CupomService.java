package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.Cliente;
import br.com.unipds.jfood.administrativo.domain.Cupom;
import br.com.unipds.jfood.administrativo.domain.CupomResgate;
import br.com.unipds.jfood.administrativo.repository.ClienteRepository;
import br.com.unipds.jfood.administrativo.repository.CupomRepository;
import br.com.unipds.jfood.administrativo.repository.CupomResgateRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CupomService {

    private final CupomRepository cupomRepository;
    private final CupomResgateRepository resgateRepository;
    private final ClienteRepository clienteRepository;

    public CupomService(CupomRepository cupomRepository,
                        CupomResgateRepository resgateRepository,
                        ClienteRepository clienteRepository) {
        this.cupomRepository = cupomRepository;
        this.resgateRepository = resgateRepository;
        this.clienteRepository = clienteRepository;
    }

    /**
     * Resgate de cupom: 100 unidades para 50 mil clientes.
     *
     * PESSIMISTA, e a justificativa esta em docs/aula05.md em uma frase: com
     * contencao proxima de 100%, o otimista transforma cada tentativa em retry, e
     * o retry de 50 mil clientes na mesma linha e uma tempestade que nao converge.
     */
    @Transactional
    public BigDecimal resgatar(String codigo, Long clienteId) {
        Cupom cupom = cupomRepository.buscarPorCodigoParaAtualizacao(codigo)
                .orElseThrow(() -> new CupomIndisponivelException("Cupom " + codigo + " nao existe"));

        if (!cupom.estaValido()) {
            throw new CupomIndisponivelException("Cupom " + codigo + " expirou");
        }
        if (!cupom.temEstoque()) {
            throw new CupomIndisponivelException("Cupom " + codigo + " esgotado");
        }
        if (resgateRepository.existsByCupomIdAndClienteId(cupom.getId(), clienteId)) {
            throw new CupomIndisponivelException("Cliente " + clienteId + " ja resgatou este cupom");
        }

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new CupomIndisponivelException("Cliente " + clienteId + " nao existe"));

        cupom.resgatar();

        CupomResgate resgate = new CupomResgate();
        resgate.setCupom(cupom);
        resgate.setCliente(cliente);
        resgateRepository.save(resgate);

        return cupom.getValorDesconto();
    }
}
