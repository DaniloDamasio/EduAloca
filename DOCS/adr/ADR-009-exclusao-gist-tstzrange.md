# ADR-009 — Exclusividade de recurso/horário garantida no PostgreSQL (`EXCLUDE USING gist` sobre `tstzrange`)

- **Status:** Aceito (10/11/2026)
- **Refina:** ADR-001 ([REVISAO_ATAM.md §3](../REVISAO_ATAM.md#3-adrs)), que já previa a constraint de exclusão sem implementá-la
- **Motivado por:** [DEF-001](../defeitos/DEF-001-dupla-reserva-entre-instancias.md)
- **Requisitos:** RF-15, RF-16, RF-17, RNF-08

## Contexto

A única proteção contra dupla reserva era um `ReentrantLock` estático no `ReservaService`, que só
funciona dentro de uma JVM. Com duas instâncias sobre o mesmo banco, cada uma aceitou uma reserva para
o mesmo recurso e horário (DEF-001: 9 de 10 casos falharam). O ADR-004 prevê instância única, mas há
sobreposição de instâncias em deploy/reinício, e a regra é crítica (Driver #2). Ela precisa valer
para qualquer processo que grave no banco.

## Alternativas

1. **Lock pessimista** (`SELECT … FOR UPDATE` na linha do recurso, ou `pg_advisory_xact_lock`).
   Funciona entre instâncias, mas depende de todo caminho de escrita lembrar de pegar o lock: um
   `UPDATE` em `alterar`, um script manual ou um endpoint novo sem o lock reabre o defeito. Salas e
   professores ainda não têm tabela no banco nesta fase, então `FOR UPDATE` exigiria criá-las
   só para servir de mutex, ou usar advisory lock por hash de id. Também serializa toda escrita do
   recurso, inclusive em intervalos que não se sobrepõem.
2. **Constraint de exclusão** `EXCLUDE USING gist (tipo_recurso WITH =, recurso_id WITH =, periodo WITH &&) WHERE (ativa)`.
   É declarativa: o banco recusa a sobreposição venha de onde vier (inserção, alteração de horário,
   reativação), e o bloqueio só ocorre entre transações que de fato disputam o mesmo recurso e horário.

## Decisão

Adotar a **alternativa 2** em `reserva_recurso`:

- `periodo tstzrange` com limites `[)`: "encostar não é sobrepor", a mesma semântica de
  `Intervalos.sobrepoe`. Escolhemos `tstzrange` e não `tsrange` porque guarda o instante absoluto. O
  domínio trabalha em hora local, convertida pelo fuso `edualoca.persistencia.fuso-horario` (padrão
  `America/Sao_Paulo`), o que evita ambiguidade se houver horário de verão.
- `ativa boolean` deixa a constraint parcial. Reservas canceladas, rejeitadas ou concluídas não
  bloqueiam o horário, e `atualizar` mantém `periodo`/`ativa` em sincronia com `reserva`.
- A violação (SQLSTATE `23P01`) é traduzida para `RegraNegocioException(409)` no
  `ReservaRepositorioJdbc`, com a mesma mensagem da checagem prévia.
- A checagem prévia (`existeConflito`) e o lock em memória permanecem como caminho rápido: dão o 409
  sem tentativa de `INSERT` no caso comum. A garantia é do banco. Com o lock desligado, os testes de
  concorrência continuam verdes.

## Consequências

- `ConcorrenciaReservaMultiInstanciaIT` (duas JVMs) e `ConcorrenciaReservaIT` ficaram verdes em 5
  execuções seguidas; `ReservaRepositorioJdbcIT` cobre a constraint isoladamente (sobreposição,
  professor, intervalos encostados, cancelamento libera, reativação recusada).
- Requer a extensão `btree_gist` (`CREATE EXTENSION`, que exige privilégio no banco) e
  duplica `inicio`/`fim`/estado em `reserva_recurso`, mantidos em sincronia na mesma transação.
- O mesmo recurso repetido dentro de uma única reserva passa a ser recusado (409), pois as duas
  linhas se sobrepõem.
- O esquema ainda é aplicado com `CREATE … IF NOT EXISTS` na subida e não altera tabelas existentes.
  Antes de haver banco com dados, adotar uma ferramenta de migração (Flyway).
