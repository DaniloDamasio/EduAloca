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
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.SenhaHasher;

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
    /** E-mail -> valor gerado por {@link SenhaHasher#gerar}; nunca a senha em claro (RNF-05, DEF-013). */
    public static final Map<String, String> credenciais = new ConcurrentHashMap<>();
    public static final Map<String, UUID> usuarioIdPorEmail = new ConcurrentHashMap<>();
    /** Perfil de cada conta; conta sem entrada aqui e Solicitante (DEF-003). */
    public static final Map<String, Perfil> perfilPorEmail = new ConcurrentHashMap<>();
    public static final Map<UUID, NotificacaoEnviada> notificacoes = new ConcurrentHashMap<>();
    public static final Map<String, UUID> responsavelPorLocalizacao = new ConcurrentHashMap<>();
    /** E-mails de contas inativadas pelo Administrador: nao fazem login e nao podem ser recadastrados. */
    public static final java.util.Set<String> usuariosInativos = ConcurrentHashMap.newKeySet();

    public static final UUID SALA_EXCLUIDA_POR_CONFLITO_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID RESPONSAVEL_BLOCO_A_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID RESPONSAVEL_BLOCO_B_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    public static final UUID ADMINISTRADOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    /** Autor das transicoes automaticas (ex.: nao comparecimento no fim do horario); nao e uma conta de login. */
    public static final UUID SISTEMA_ID = UUID.fromString("00000000-0000-0000-0000-00000000005e");

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
            perfilPorEmail.clear();
            notificacoes.clear();
            responsavelPorLocalizacao.clear();
            usuariosInativos.clear();
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

            credenciais.putAll(HashesSeed.SOLICITANTES);
            contaSeed("responsavel@example.com", RESPONSAVEL_BLOCO_A_ID, Perfil.RESPONSAVEL);
            contaSeed("responsavel.b@example.com", RESPONSAVEL_BLOCO_B_ID, Perfil.RESPONSAVEL);
            contaSeed("admin@example.com", ADMINISTRADOR_ID, Perfil.ADMINISTRADOR);

            responsavelPorLocalizacao.put("Bloco A", RESPONSAVEL_BLOCO_A_ID);
            responsavelPorLocalizacao.put("Bloco B", RESPONSAVEL_BLOCO_B_ID);

            seeded = true;
        }
    }

    /** Cadastra (ou troca) a credencial guardando so o hash com sal. */
    public static void registrarCredencial(String email, String senhaEmTextoPlano) {
        credenciais.put(email, SenhaHasher.gerar(senhaEmTextoPlano));
    }

    private static void contaSeed(String email, UUID id, Perfil perfil) {
        credenciais.put(email, HashesSeed.PERFIS.get(email));
        usuarioIdPorEmail.put(email, id);
        perfilPorEmail.put(email, perfil);
    }

    /**
     * Hashes das contas de demonstracao, calculados uma vez por JVM (cada hash custa ~50 ms e o seed roda a cada
     * {@link #resetParaTeste()}). As senhas em claro existem so neste inicializador.
     */
    private static final class HashesSeed {
        static final Map<String, String> SOLICITANTES = Map.of(
                "usuario@example.com", SenhaHasher.gerar("senhaValida123"),
                "novo@example.com", SenhaHasher.gerar("minhaSenha123"));
        static final Map<String, String> PERFIS = Map.of(
                "responsavel@example.com", SenhaHasher.gerar("senhaResponsavel123"),
                "responsavel.b@example.com", SenhaHasher.gerar("senhaResponsavelB123"),
                "admin@example.com", SenhaHasher.gerar("senhaAdmin123"));

        private HashesSeed() {
        }
    }
}
