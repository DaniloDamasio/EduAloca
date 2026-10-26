package com.edualoca.domain.retirada;

import java.time.LocalDateTime;
import java.util.UUID;

public class RegistroRetirada {

    private final UUID id;
    private final UUID reservaId;
    private final UUID autorId;
    private final LocalDateTime dataRetirada;
    private LocalDateTime dataDevolucao;
    private CondicaoItem condicaoDevolucao;
    private boolean pendenciaSinalizada = false;

    public RegistroRetirada(UUID id, UUID reservaId, UUID autorId, LocalDateTime dataRetirada) {
        this.id = id;
        this.reservaId = reservaId;
        this.autorId = autorId;
        this.dataRetirada = dataRetirada;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReservaId() {
        return reservaId;
    }

    public UUID getAutorId() {
        return autorId;
    }

    public LocalDateTime getDataRetirada() {
        return dataRetirada;
    }

    public LocalDateTime getDataDevolucao() {
        return dataDevolucao;
    }

    public void setDataDevolucao(LocalDateTime dataDevolucao) {
        this.dataDevolucao = dataDevolucao;
    }

    public CondicaoItem getCondicaoDevolucao() {
        return condicaoDevolucao;
    }

    public void setCondicaoDevolucao(CondicaoItem condicaoDevolucao) {
        this.condicaoDevolucao = condicaoDevolucao;
    }

    public boolean isPendenciaSinalizada() {
        return pendenciaSinalizada;
    }

    public void setPendenciaSinalizada(boolean pendenciaSinalizada) {
        this.pendenciaSinalizada = pendenciaSinalizada;
    }
}
