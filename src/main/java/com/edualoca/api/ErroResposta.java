package com.edualoca.api;

/** Corpo de erro unico da API: so a mensagem em linguagem natural, sem detalhe interno (RF-38, RNF-06). */
public record ErroResposta(String mensagem) {
}
