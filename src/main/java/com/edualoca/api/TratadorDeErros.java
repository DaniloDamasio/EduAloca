package com.edualoca.api;

import com.edualoca.domain.comum.RegraNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduz excecoes em respostas HTTP compreensiveis no formato ProblemDetail (RFC 9457,
 * {@code application/problem+json}). Nenhuma resposta leva stacktrace, nome de classe ou mensagem tecnica do
 * framework; o detalhe de erro inesperado fica so no log do servidor (RF-38, RNF-06).
 *
 * <p>Alem dos campos do padrao ({@code type}, {@code title}, {@code status}, {@code detail}, {@code instance}), o
 * corpo repete o {@code detail} na propriedade {@code mensagem}, o campo do contrato anterior a DEF-002, para nao
 * quebrar clientes existentes.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ProblemDetail> regraDeNegocio(RegraNegocioException e) {
        return problema(e.getStatusHttp(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> corpoIlegivel(HttpMessageNotReadableException e) {
        return problema(400, "Requisicao invalida: verifique os campos e o formato das datas (aaaa-mm-ddThh:mm)");
    }

    /**
     * DEF-008: nomeia o parametro, que e contrato publico da API (o mesmo nome que o cliente envia na URL). O valor
     * recebido nao e ecoado, para nao refletir entrada do cliente na resposta.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> parametroAusente(MissingServletRequestParameterException e) {
        return problema(400, "Parametro obrigatorio ausente: informe '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return problema(400, "Valor invalido para '" + e.getName()
                + "': use numero inteiro, UUID ou data no formato aaaa-mm-ddThh:mm, conforme o campo");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(Exception e) {
        if (e instanceof ErrorResponse erroDoFramework) {
            // 404 (rota inexistente), 405 (metodo), 415 (tipo de midia) etc.: mantem o status, sem detalhe interno.
            int status = erroDoFramework.getStatusCode().value();
            return problema(status, mensagemPara(status));
        }
        LOG.error("Erro inesperado ao processar requisicao", e);
        return problema(500, "Nao foi possivel concluir a operacao. Tente novamente mais tarde.");
    }

    private static ResponseEntity<ProblemDetail> problema(int status, String mensagem) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), mensagem);
        problema.setTitle(tituloPara(status));
        problema.setProperty("mensagem", mensagem);
        return ResponseEntity.status(status).body(problema);
    }

    private static String tituloPara(int status) {
        return switch (status) {
            case 400 -> "Requisicao invalida";
            case 401 -> "Autenticacao necessaria";
            case 403 -> "Acesso negado";
            case 404 -> "Nao encontrado";
            case 405 -> "Operacao nao permitida";
            case 409 -> "Conflito";
            case 415 -> "Formato nao suportado";
            case 500 -> "Erro interno";
            default -> "Requisicao nao atendida";
        };
    }

    private static String mensagemPara(int status) {
        return switch (status) {
            case 404 -> "Endereco da API nao encontrado";
            case 405 -> "Operacao nao permitida para este endereco";
            case 415 -> "Formato da requisicao nao suportado: envie JSON";
            default -> "Requisicao nao pode ser atendida";
        };
    }
}
