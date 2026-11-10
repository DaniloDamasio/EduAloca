package com.edualoca.domain.recurso;

import java.time.LocalDateTime;
import java.util.UUID;

public class Manutencao {

    private final UUID id;
    private final Object recurso;
    private final LocalDateTime inicio;
    private final LocalDateTime fim;
    private final String motivo;

    public Manutencao(UUID id, Object recurso, LocalDateTime inicio, LocalDateTime fim, String motivo) {
        this.id = id;
        this.recurso = recurso;
        this.inicio = inicio;
        this.fim = fim;
        this.motivo = motivo;
    }

    public UUID getId() {
        return id;
    }

    public Sala getSala() {
        return recurso instanceof Sala sala ? sala : null;
    }

    public Object getRecurso() {
        return recurso;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public String getMotivo() {
        return motivo;
    }
}
