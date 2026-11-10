-- Esquema das reservas no PostgreSQL (arquitetura.md §11). Idempotente: executado na subida da aplicacao.

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

CREATE TABLE IF NOT EXISTS reserva_recurso (
    id           bigserial    PRIMARY KEY,
    reserva_id   uuid         NOT NULL REFERENCES reserva (id),
    tipo_recurso varchar(20)  NOT NULL,
    recurso_id   uuid         NOT NULL,
    quantidade   integer      NOT NULL CHECK (quantidade > 0)
);

CREATE INDEX IF NOT EXISTS ix_reserva_recurso_recurso ON reserva_recurso (tipo_recurso, recurso_id);
