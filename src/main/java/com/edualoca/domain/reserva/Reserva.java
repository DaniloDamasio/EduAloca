package com.edualoca.domain.reserva;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class Reserva {

    private final UUID id;
    private final UUID solicitanteId;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private final String finalidade;
    private final List<ReservaRecurso> recursos;
    private EstadoReserva estado;

    public Reserva(UUID id, UUID solicitanteId, LocalDateTime inicio, LocalDateTime fim,
                    String finalidade, List<ReservaRecurso> recursos, EstadoReserva estado) {
        this.id = id;
        this.solicitanteId = solicitanteId;
        this.inicio = inicio;
        this.fim = fim;
        this.finalidade = finalidade;
        this.recursos = recursos;
        this.estado = estado;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSolicitanteId() {
        return solicitanteId;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public List<ReservaRecurso> getRecursos() {
        return recursos;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public void setEstado(EstadoReserva estado) {
        this.estado = estado;
    }

    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public void setFim(LocalDateTime fim) {
        this.fim = fim;
    }
}
