-- Esquema das reservas no PostgreSQL (arquitetura.md §11). Idempotente: executado na subida da aplicacao.

-- Igualdade de uuid/varchar dentro de um indice GiST (necessario para a constraint de exclusao).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS reserva (
    id                     uuid         PRIMARY KEY,
    solicitante_id         uuid         NOT NULL,
    inicio                 timestamp    NOT NULL,
    fim                    timestamp    NOT NULL,
    finalidade             text         NOT NULL,
    estado                 varchar(20)  NOT NULL,
    justificativa_rejeicao text,
    CONSTRAINT ck_reserva_intervalo CHECK (fim > inicio)
);

-- periodo e ativa replicam, por recurso, o intervalo e o estado da reserva, para que a regra
-- "um recurso nao tem duas reservas ativas sobrepostas" (RF-15/RF-17) seja garantida pelo proprio
-- banco, independente de quantas instancias da aplicacao gravem (DEF-001, ADR-009).
CREATE TABLE IF NOT EXISTS reserva_recurso (
    id           bigserial    PRIMARY KEY,
    reserva_id   uuid         NOT NULL REFERENCES reserva (id),
    tipo_recurso varchar(20)  NOT NULL,
    recurso_id   uuid         NOT NULL,
    quantidade   integer      NOT NULL CHECK (quantidade > 0),
    periodo      tstzrange    NOT NULL CHECK (NOT isempty(periodo)),
    ativa        boolean      NOT NULL,
    CONSTRAINT ex_reserva_recurso_sem_sobreposicao
        EXCLUDE USING gist (tipo_recurso WITH =, recurso_id WITH =, periodo WITH &&) WHERE (ativa)
);

-- Historico auditavel das mudancas de estado (RF-27). Gravado na mesma transacao que altera a reserva.
-- Append-only (RF-29): a API nao expoe alteracao/exclusao e o trigger abaixo recusa UPDATE e DELETE no banco.
CREATE TABLE IF NOT EXISTS reserva_historico (
    sequencia       bigint       GENERATED ALWAYS AS IDENTITY,
    id              uuid         PRIMARY KEY,
    reserva_id      uuid         NOT NULL REFERENCES reserva (id),
    usuario_id      uuid         NOT NULL,
    instante        timestamptz  NOT NULL,
    estado_anterior varchar(20),
    estado_novo     varchar(20)  NOT NULL,
    motivo          text         NOT NULL CHECK (btrim(motivo) <> ''),
    CONSTRAINT ck_reserva_historico_transicao CHECK (estado_anterior IS DISTINCT FROM estado_novo)
);

CREATE INDEX IF NOT EXISTS ix_reserva_historico_reserva ON reserva_historico (reserva_id, sequencia);

-- Corpo entre aspas simples (e nao $$) porque o ScriptUtils do Spring nao reconhece dollar-quoting ao separar comandos.
CREATE OR REPLACE FUNCTION recusar_alteracao_historico() RETURNS trigger LANGUAGE plpgsql AS
'BEGIN
    RAISE EXCEPTION ''reserva_historico e somente inclusao: % recusado'', TG_OP USING ERRCODE = ''insufficient_privilege'';
END';

CREATE OR REPLACE TRIGGER tg_reserva_historico_somente_inclusao
    BEFORE UPDATE OR DELETE ON reserva_historico
    FOR EACH ROW EXECUTE FUNCTION recusar_alteracao_historico();
