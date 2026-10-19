# Modelagem de Dados — EduAloca

> Modelo conceitual/lógico derivado dos requisitos em [REQUISITOS.md](REQUISITOS.md) e das
> entidades citadas no [enunciado.md](enunciado.md). Tipos são indicativos (a ser refinado
> na implementação em Java 21/Spring Boot/JPA), suficientes para validar relacionamentos e
> regras críticas de concorrência/auditoria.
>
> Versão navegável do diagrama: [MODELAGEM_DADOS.dbml](MODELAGEM_DADOS.dbml) — cole o
> conteúdo em [dbdiagram.io](https://dbdiagram.io) (botão "Import DBML") para visualizar e
> editar o ER graficamente. Mantenha as duas versões (tabelas abaixo e `.dbml`)
> sincronizadas ao alterar o modelo.

## Entidades principais

### Usuario
Representa qualquer pessoa autenticada no sistema — Solicitante, Responsável ou
Administrador. (Suporta RF-01, RF-02)

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| nome | String | |
| email | String (único) | usado como login |
| senhaHash | String | hash BCrypt (RNF-05), nunca texto plano |
| perfil | Enum(SOLICITANTE, RESPONSAVEL, ADMINISTRADOR) | define autorização (RF-02) |
| ativo | Boolean | soft delete / desativação (cenário "desativar professor") |
| criadoEm / atualizadoEm | Timestamp | |

### Professor
Entidade de recurso "professor", distinta de `Usuario` (um professor pode ou não ter login
no sistema como Solicitante). (Suporta RF-05)

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| nome | String | |
| identificadorInstitucional | String (único) | matrícula |
| usuarioId | FK → Usuario (nullable) | vínculo opcional com conta de login |
| ativo | Boolean | |

### Competencia
Catálogo de competências que um professor pode ter e que uma atividade pode exigir.
(Suporta RF-09)

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| nome | String (único) | ex.: "Robótica", "Língua Inglesa" |

### ProfessorCompetencia (associativa N:N)

| Campo | Tipo |
|---|---|
| professorId | FK → Professor |
| competenciaId | FK → Competencia |

### Sala

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| nome | String | |
| tipo | String/Enum | ex.: laboratório, auditório, sala comum |
| capacidade | Integer | |
| localizacao | String | ex.: bloco/andar |
| restrito | Boolean | exige aprovação (RF-18) |
| ativo | Boolean | |

### Material

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| nome | String | |
| tipo | String/Enum | ex.: projetor, notebook, kit de laboratório |
| quantidadeTotal | Integer | |
| restrito | Boolean | |
| ativo | Boolean | |

### Reserva
Entidade central. Uma reserva referencia um ou mais recursos (sala, professor(es),
material(is)) — modelada via tabela associativa `ReservaRecurso` para suportar
combinações livres exigidas pelo enunciado ("salas, professores e materiais").

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| solicitanteId | FK → Usuario | autor da reserva (RF-03) |
| inicio | Timestamp | deve ser < fim (regra crítica) |
| fim | Timestamp | |
| finalidade | String | descrição da atividade |
| estado | Enum(SOLICITADA, APROVADA, EM_USO, CONCLUIDA, REJEITADA, CANCELADA, NAO_COMPARECEU) | máquina de estados (J9) |
| exigeAprovacao | Boolean | derivado: true se algum recurso vinculado é restrito |
| aprovadoPorId | FK → Usuario (nullable) | Responsável/Administrador que decidiu |
| motivoRejeicaoOuCancelamento | String (nullable) | obrigatório em rejeição (RF-20) e cancelamento (RF-14) |
| versao | Integer | controle de concorrência otimista (RNF-08, RF-17) |
| criadoEm / atualizadoEm | Timestamp | |

**Restrição de integridade crítica:** `CHECK (fim > inicio)` no banco, reforçando a regra
"O término deve ser posterior ao início".

### ReservaRecurso (associativa polimórfica)
Liga uma reserva a cada recurso (sala, professor, material) que ela ocupa, permitindo
validar sobreposição por recurso individualmente (RF-15, RF-16).

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| reservaId | FK → Reserva | |
| tipoRecurso | Enum(SALA, PROFESSOR, MATERIAL) | |
| recursoId | Long | id da Sala, Professor ou Material correspondente |
| quantidade | Integer (nullable) | aplicável a Material (ex.: 3 notebooks) |

**Índice crítico:** `(tipoRecurso, recursoId, inicio, fim)` (via join com Reserva) deve ser
consultável eficientemente para checagem de sobreposição — ver RNF-01/RNF-08.

**Constraint de não-sobreposição:** implementada via `EXCLUDE USING gist` (PostgreSQL,
tsrange) ou lock + revalidação na camada de serviço, garantindo RF-15/RF-17 mesmo sob
concorrência.

### Manutencao
Período de bloqueio de um recurso. (Suporta RF-21, RF-22, RF-23)

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| tipoRecurso | Enum(SALA, PROFESSOR, MATERIAL) | |
| recursoId | Long | |
| inicio | Timestamp | |
| fim | Timestamp | |
| motivo | String | |
| criadoPorId | FK → Usuario | Administrador |

### RetiradaDevolucao
Controle de ciclo de vida de material físico vinculado a uma reserva. (Suporta RF-24,
RF-25, RF-26)

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| reservaRecursoId | FK → ReservaRecurso | item de material específico |
| retiradoEm | Timestamp (nullable) | |
| retiradoPorId | FK → Usuario (nullable) | |
| devolvidoEm | Timestamp (nullable) | |
| condicaoDevolucao | Enum(INTEGRO, DANIFICADO, NAO_DEVOLVIDO) (nullable) | |
| registradoPorId | FK → Usuario | Responsável que operou o registro |

### AuditoriaEvento
Log imutável de toda mudança relevante (estado de reserva ou cadastro). (Suporta RF-27,
RF-28, RF-29)

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| entidade | Enum(RESERVA, SALA, PROFESSOR, MATERIAL, USUARIO, MANUTENCAO) | |
| entidadeId | Long | |
| autorId | FK → Usuario | |
| perfilAutor | Enum | perfil no momento da ação |
| acao | String | ex.: CRIACAO, APROVACAO, REJEICAO, CANCELAMENTO, ATUALIZACAO, INATIVACAO |
| estadoAnterior | String (nullable) | aplicável a reservas |
| estadoNovo | String (nullable) | aplicável a reservas |
| motivo | String (nullable) | |
| dataHora | Timestamp | |

**Regra de imutabilidade:** nenhuma API de UPDATE/DELETE é exposta sobre esta entidade
(RF-29) — inserções apenas (append-only).

### NotificacaoEnviada
Registro de efeito colateral de notificação, para suportar RF-31/RF-32/RF-33 e testes com
WireMock.

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID/Long (PK) | |
| destinatarioId | FK → Usuario | |
| reservaId | FK → Reserva (nullable) | |
| canal | Enum(SIMULADO, API_EXTERNA) | |
| status | Enum(ENVIADA, FALHA) | falha não reverte transação de negócio (RF-33) |
| enviadoEm | Timestamp | |

---

## Diagrama ER (visão lógica simplificada)

```
Usuario (1) ──< Reserva (solicitante)
Usuario (1) ──< Reserva (aprovadoPor, 0..1)
Usuario (1) ──< AuditoriaEvento (autor)
Usuario (1) ──< NotificacaoEnviada (destinatario)
Usuario (0..1) ── Professor (vínculo de login opcional)

Professor (N) ──< ProfessorCompetencia >── (N) Competencia

Reserva (1) ──< ReservaRecurso >── referencia polimórfica → Sala | Professor | Material
Reserva (1) ──< AuditoriaEvento (entidade=RESERVA)
Reserva (1) ──< NotificacaoEnviada

ReservaRecurso (1) ──< RetiradaDevolucao (quando tipoRecurso = MATERIAL)

Sala / Professor / Material (1) ──< Manutencao
Sala / Professor / Material (1) ──< AuditoriaEvento (entidade correspondente)
```

## Decisões de modelagem e justificativa

- **ReservaRecurso como associativa polimórfica**: o enunciado exige que uma única reserva
  possa envolver sala, professor e material simultaneamente (ex.: "sala + projetor +
  professor convidado"), e que a sobreposição seja checada por recurso individual (RF-15,
  RF-16). Uma FK única em `Reserva` não suportaria múltiplos recursos nem tipos distintos.
- **Reserva nunca é excluída fisicamente** (RF-30): a tabela não possui operação de
  DELETE exposta; `estado = CANCELADA/REJEITADA` é a forma de "encerrar" uma reserva.
- **AuditoriaEvento é append-only e desacoplada por entidade genérica**: evita duplicar
  tabelas de histórico para cada entidade (reserva, sala, professor, etc.), centralizando
  a trilha auditável exigida (RF-27, RF-28, RF-29).
- **Campo `versao` em Reserva**: suporta controle de concorrência otimista (`@Version` em
  JPA), peça-chave para a proteção contra dupla reserva validada por teste (RF-17,
  RNF-08).
- **`exigeAprovacao` derivado e persistido na Reserva**: evita recalcular a cada leitura se
  algum `ReservaRecurso` vinculado é restrito; simplifica a consulta de fila de pendências
  do Responsável (RF-19).
- **`NotificacaoEnviada` como entidade própria**: permite verificar via teste
  (WireMock/assert em banco) que a notificação foi tentada, sem acoplar o sucesso dela à
  transação da reserva (RF-33).
