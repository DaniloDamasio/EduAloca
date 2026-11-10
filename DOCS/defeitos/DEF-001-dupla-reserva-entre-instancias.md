# DEF-001 — Dupla reserva quando há mais de uma instância da aplicação

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 10/11/2026) |
| Severidade | Alta: viola a regra crítica "duas solicitações simultâneas produzem uma única reserva aceita" (RF-17, RF-15, Driver #2) |
| Prioridade | Alta |
| Requisitos | RF-15, RF-16, RF-17, RNF-08 |
| Componente | `ReservaService` (lock em memória), esquema `reserva`/`reserva_recurso` no PostgreSQL |
| Encontrado em | 10/11/2026, branch `concorrencia-reservas-testcontainers`, commit `c0732db` |
| Teste que detecta | `ConcorrenciaReservaMultiInstanciaIT` (salas e agenda do professor) |

## Resumo

A exclusividade de recurso/horário é garantida apenas por um `ReentrantLock` estático dentro do
`ReservaService`, que só vale dentro de uma JVM. O banco não tem nenhuma restrição de sobreposição.
Com duas instâncias da aplicação sobre o mesmo PostgreSQL, cada uma aceita uma reserva para o mesmo
recurso e o mesmo intervalo, e o banco grava as duas.

Com uma instância só, `ConcorrenciaReservaIT` passa (5/5 execuções). O defeito aparece quando há
duas instâncias, o que acontece mesmo sob o ADR-004 ("instância única"): na sobreposição de um
deploy/reinício, numa réplica criada por engano e em qualquer outro caminho de escrita que não
passe por esse lock.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, Testcontainers 2.0.5
- PostgreSQL `postgres:16-alpine` via Testcontainers; Docker 29.5.2 em Colima (macOS)
- Instância A: contexto Spring do teste (porta aleatória); instância B: outra JVM
  (`com.edualoca.suporte.InstanciaExterna`), mesmo banco

## Passos para reproduzir

1. Subir o PostgreSQL e duas instâncias da aplicação apontando para ele
   (`edualoca.persistencia.url`, `EDUALOCA_PERSISTENCIA_USUARIO`, `EDUALOCA_PERSISTENCIA_SENHA`).
2. Cadastrar a mesma sala (ou o mesmo professor) nas duas instâncias.
3. Disparar ao mesmo tempo 20 `POST /reservas`, 10 para cada instância, todos com o mesmo recurso e
   o intervalo `2027-03-15T08:00`–`2027-03-15T10:00`.
4. Contar as respostas por status e consultar o banco:
   ```sql
   SELECT count(*) FROM reserva r JOIN reserva_recurso rr ON rr.reserva_id = r.id
    WHERE rr.tipo_recurso = 'SALA' AND rr.recurso_id = :sala
      AND r.inicio = '2027-03-15 08:00' AND r.fim = '2027-03-15 10:00';
   ```

Automatizado:

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw -B verify -Djacoco.skip=true -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=ConcorrenciaReservaMultiInstanciaIT
```

## Resultado esperado

1 resposta `201`, 19 respostas `409` e **1** linha no banco para o recurso e o intervalo.

## Resultado obtido

Em cada instância uma reserva é aceita: **2** respostas `201`, 18 `409`, **2** linhas no banco.
Cinco execuções seguidas, antes da correção:

| Execução | Sala | Professor | Respostas (porta→status), caso da sala |
|---|---|---|---|
| 1 | Falhou (2 linhas) | Falhou (2 linhas) | `{60812→409=9, 60802→409=9, 60802→201=1, 60812→201=1}` |
| 2 | Falhou (2 linhas) | Falhou (2 linhas) | `{60894→201=1, 60885→409=9, 60894→409=9, 60885→201=1}` |
| 3 | Falhou (2 linhas) | Falhou (2 linhas) | `{61002→409=9, 61011→409=9, 61011→201=1, 61002→201=1}` |
| 4 | Falhou (2 linhas) | Falhou (2 linhas) | `{61072→409=9, 61082→201=1, 61082→409=9, 61072→201=1}` |
| 5 | Falhou (2 linhas) | Passou | `{61148→201=1, 61140→201=1, 61148→409=9, 61140→409=9}` |

Total: 9 de 10 casos falharam. Trecho do relatório do Failsafe (execução 1):

```
ConcorrenciaReservaMultiInstanciaIT  Tests run: 2, Failures: 2
AssertionFailedError: respostas 201 esperadas: 1; obtido (porta->status):
  {60812->409=9, 60802->409=9, 60802->201=1, 60812->201=1} ==> expected: <1> but was: <2>
AssertionFailedError: linhas no banco para PROFESSOR e3728f48-... em
  [2027-03-15T08:00, 2027-03-15T10:00) ==> expected: <1> but was: <2>
AssertionFailedError: linhas no banco para SALA 113de001-... em
  [2027-03-15T08:00, 2027-03-15T10:00) ==> expected: <1> but was: <2>
```

Evidência complementar: com uma única instância e o `ReentrantLock` desligado (sonda temporária,
não versionada), `ConcorrenciaReservaIT` grava 19–20 linhas. Ou seja, o lock é a **única** proteção.

## Causa raiz

A verificação de conflito (`SELECT`) e a gravação (`INSERT`) são passos separados. Elas só ficam
atômicas por causa do lock em memória do processo. O esquema não tem restrição de sobreposição, ao
contrário do decidido no ADR-001 e em arquitetura.md §11 (`EXCLUDE USING gist`), que não chegou a ser
implementado. Entre processos nada impede que duas transações vejam "sem conflito" e gravem.

## Correção

Constraint de exclusão no PostgreSQL em `reserva_recurso`:
`EXCLUDE USING gist (tipo_recurso WITH =, recurso_id WITH =, periodo WITH &&) WHERE (ativa)`, com
`periodo tstzrange` `[)`. A violação (SQLSTATE `23P01`) é traduzida para `409` em
`ReservaRepositorioJdbc`. Decisão e alternativas no [ADR-009](../adr/ADR-009-exclusao-gist-tstzrange.md).
Nenhum teste foi alterado para a correção.

## Reteste

Cinco execuções seguidas de `ConcorrenciaReservaMultiInstanciaIT` e `ConcorrenciaReservaIT`, sem
alterar os testes:

| Execução | Multi-instância (sala, professor) | Instância única (sala, professor) |
|---|---|---|
| 1 | 2/2 verdes | 2/2 verdes |
| 2 | 2/2 verdes | 2/2 verdes |
| 3 | 2/2 verdes | 2/2 verdes |
| 4 | 2/2 verdes | 2/2 verdes |
| 5 | 2/2 verdes | 2/2 verdes |

Sonda de confirmação: com o `ReentrantLock` do `ReservaService` desligado (alteração temporária, não
versionada), os dois testes ficaram verdes em 3 execuções. A garantia agora vem do banco, não do
lock. `ReservaRepositorioJdbcIT` cobre a constraint diretamente (4 casos).
