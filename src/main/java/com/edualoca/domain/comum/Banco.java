package com.edualoca.domain.comum;

import com.edualoca.domain.auditoria.AuditoriaEvento;
import com.edualoca.domain.notificacao.NotificacaoEnviada;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Manutencao;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.reserva.Reserva;
import com.edualoca.domain.retirada.RegistroRetirada;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio em memoria compartilhado entre os servicos de dominio, simulando a
 * persistencia unica (ADR-001/arquitetura.md) ainda nao disponivel nesta fase (sem JPA).
 */
public final class Banco {

    public static final Map<UUID, Sala> salas = new ConcurrentHashMap<>();
    public static final Map<UUID, Professor> professores = new ConcurrentHashMap<>();
    public static final Map<String, UUID> professorIdPorIdentificador = new ConcurrentHashMap<>();
    public static final Map<UUID, Material> materiais = new ConcurrentHashMap<>();
    public static final Map<UUID, Manutencao> manutencoes = new ConcurrentHashMap<>();
    public static final Map<UUID, Reserva> reservas = new ConcurrentHashMap<>();
    public static final List<AuditoriaEvento> auditoria = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final Map<UUID, RegistroRetirada> retiradas = new ConcurrentHashMap<>();
    public static final Map<String, String> credenciais = new ConcurrentHashMap<>();
    public static final Map<String, UUID> usuarioIdPorEmail = new ConcurrentHashMap<>();
    public static final Map<UUID, NotificacaoEnviada> notificacoes = new ConcurrentHashMap<>();
    public static final Map<String, UUID> responsavelPorLocalizacao = new ConcurrentHashMap<>();

    public static final UUID SALA_EXCLUIDA_POR_CONFLITO_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final Object SEED_LOCK = new Object();
    private static volatile boolean seeded = false;

    private Banco() {
    }

    public static void resetParaTeste() {
        synchronized (SEED_LOCK) {
            salas.clear();
            professores.clear();
            professorIdPorIdentificador.clear();
            materiais.clear();
            manutencoes.clear();
            reservas.clear();
            auditoria.clear();
            retiradas.clear();
            credenciais.clear();
            usuarioIdPorEmail.clear();
            notificacoes.clear();
            responsavelPorLocalizacao.clear();
            seeded = false;
        }
    }

    public static void seedSeNecessario() {
        if (seeded) {
            return;
        }
        synchronized (SEED_LOCK) {
            if (seeded) {
                return;
            }
            Sala salaSeed = new Sala(UUID.randomUUID(), "Laboratorio 1", "laboratorio", 20, "Bloco A", false);
            salas.put(salaSeed.getId(), salaSeed);

            Sala salaExcluida = new Sala(SALA_EXCLUIDA_POR_CONFLITO_ID, "Laboratorio Indisponivel", "laboratorio", 20, "Bloco A", false);
            salas.put(salaExcluida.getId(), salaExcluida);
            Manutencao manutencaoSeed = new Manutencao(UUID.randomUUID(), salaExcluida,
                    LocalDateTime.of(2026, 3, 10, 9, 0), LocalDateTime.of(2026, 3, 10, 13, 0), "manutencao de exemplo");
            manutencoes.put(manutencaoSeed.getId(), manutencaoSeed);

            Professor professorSeed = new Professor(UUID.randomUUID(), "Prof. Seed", "SEED-001",
                    List.of(new Competencia("Robotica")));
            professores.put(professorSeed.getId(), professorSeed);
            professorIdPorIdentificador.put(professorSeed.getIdentificadorInstitucional(), professorSeed.getId());

            Material materialSeed = new Material(UUID.randomUUID(), "Projetor Seed", "projetor", 3, false);
            materiais.put(materialSeed.getId(), materialSeed);

            credenciais.put("usuario@example.com", "senhaValida123");
            credenciais.put("novo@example.com", "minhaSenha123");

            responsavelPorLocalizacao.put("Bloco A", UUID.randomUUID());

            seeded = true;
        }
    }
}
