package br.com.unipds.jfood.administrativo.web;

import br.com.unipds.jfood.administrativo.service.CupomIndisponivelException;
import br.com.unipds.jfood.administrativo.service.PedidoNaoEncontradoException;
import br.com.unipds.jfood.administrativo.service.StatusPedidoInvalidoException;
import br.com.unipds.jfood.administrativo.service.gateway.PagamentoRecusadoException;
import org.springframework.dao.OptimisticLockingFailureException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PedidoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNaoEncontrado(PedidoNaoEncontradoException ex) {
        return Map.of("erro", ex.getMessage());
    }

    /**
     * O conflito otimista visto pelo cliente: 409, com a instrucao de recarregar.
     *
     * O 409 nao e detalhe de protocolo -- e a unica resposta honesta. O servidor
     * nao pode decidir sozinho qual das duas edicoes vale.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflitoOtimista(OptimisticLockingFailureException ex) {
        return Map.of(
                "erro", "Conflito de concorrencia detectado.",
                "mensagem", "O pedido foi modificado por outra operacao. Recarregue e tente novamente.");
    }

    @ExceptionHandler(StatusPedidoInvalidoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleStatusInvalido(StatusPedidoInvalidoException ex) {
        return Map.of("erro", ex.getMessage());
    }

    @ExceptionHandler(CupomIndisponivelException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleCupomIndisponivel(CupomIndisponivelException ex) {
        return Map.of("erro", ex.getMessage());
    }

    @ExceptionHandler(PagamentoRecusadoException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public Map<String, String> handlePagamentoRecusado(PagamentoRecusadoException ex) {
        return Map.of("erro", ex.getMessage());
    }
}
