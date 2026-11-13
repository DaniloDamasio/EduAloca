package com.edualoca.domain.usuario;

import java.time.Instant;

/** Falhas de login de um e-mail na janela corrente e, se houver, ate quando o login esta bloqueado. */
public record TentativasLogin(int falhas, Instant inicioDaJanela, Instant bloqueadoAte) {
}
