package com.edualoca.suporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relogio de teste parado num instante que o teste avanca explicitamente; sem dependencia do relogio real. */
public class RelogioAjustavel extends Clock {

    private Instant agora;
    private final ZoneId zona;

    public RelogioAjustavel(Instant inicial, ZoneId zona) {
        this.agora = inicial;
        this.zona = zona;
    }

    public void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId outraZona) {
        return new RelogioAjustavel(agora, outraZona);
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
