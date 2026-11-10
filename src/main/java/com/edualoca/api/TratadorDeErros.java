package com.edualoca.api;

import com.edualoca.domain.comum.RegraNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduz excecoes em respostas HTTP compreensiveis. Nenhuma resposta leva stacktrace, nome de classe
 * ou mensagem tecnica do framework; o detalhe de erro inesperado fica so no log do servidor.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResposta> regraDeNegocio(RegraNegocioException e) {
        return ResponseEntity.status(e.getStatusHttp()).body(new ErroResposta(e.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErroResposta> entradaInvalida(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ErroResposta("Requisicao invalida: verifique os campos e o formato das datas (aaaa-mm-ddThh:mm)"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> inesperado(Exception e) {
        LOG.error("Erro inesperado ao processar requisicao", e);
        return ResponseEntity.internalServerError()
                .body(new ErroResposta("Nao foi possivel concluir a operacao. Tente novamente mais tarde."));
    }
}
