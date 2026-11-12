# DEF-011 — Consulta de auditoria inventa evento para entidade sem histórico

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 11/11/2026, local e no CI: run 43125582350, commit `d69901d`) |
| Severidade | Alta: a trilha de auditoria afirma ações que nunca aconteceram (`REJEICAO_SOBREPOSICAO` ou `CRIACAO`, com autor aleatório) e esconde a ausência de registro, que é justamente o que a auditoria precisa revelar |
| Prioridade | Alta |
| Requisitos | RF-27, RF-28, RF-29 |
| Componente | `AuditoriaService.consultarPorEntidade` |
| Encontrado em | 11/11/2026, branch `auditoria-mutacao`, commit-base `ee540c5`, durante a [mutação manual](../testes/mutacao-manual.md) (lacuna U8) |
| Teste que detecta | `AuditoriaTest` :: `def011_entidadeSemEventos_devolveListaVazia` |

## Resumo

Quando a entidade consultada não tem nenhum evento, `consultarPorEntidade` devolve um evento fabricado na hora: ação
`REJEICAO_SOBREPOSICAO` para `Reserva` e `CRIACAO` para qualquer outra entidade, com `autorId` aleatório e o instante
atual. Por causa disso, três testes de `AuditoriaTest` (`rf27T1`, `rf28T1`, `rf28T2`) consultavam um UUID aleatório
e passavam com qualquer código de produção. Na mutação U8 (remover a auditoria do cadastro de manutenção), a suíte
inteira continuou verde.

## Passos para reproduzir

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=AuditoriaTest#def011_entidadeSemEventos_devolveListaVazia
```

## Resultado esperado

Lista vazia para entidade sem eventos.

## Resultado obtido

```
eventos inventados para reserva: [com.edualoca.domain.auditoria.AuditoriaEvento@13d9b21f] ==> expected: <true> but was: <false>
```

## Causa raiz

[AuditoriaService.java:40-44](../../src/main/java/com/edualoca/domain/auditoria/AuditoriaService.java#L40-L44): um
*fallback* que cria `new AuditoriaEvento(...)` quando o filtro não acha nada. Ele só existia para satisfazer os
testes de id aleatório.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- Teste de unidade sobre `AuditoriaService` e `ReservaService`

## Correção

Commit `fc5fe22`. `consultarPorEntidade` devolve só os eventos registrados, ou seja, lista vazia quando não há
nenhum. Saíram o *fallback* e o `Clock` da instância, que só ele usava. O registro de eventos (`registrar`) não mudou.

No commit red `1826216`, `rf27T1`, `rf28T1` e `rf28T2` foram reescritos para exercitar ações reais (aprovar uma
reserva restrita, cadastrar uma sala, provocar uma rejeição por sobreposição) e conferir a ação e o autor do evento.

## Reteste

- `def011_entidadeSemEventos_devolveListaVazia` está verde.
- `./mvnw -B verify` local: 387 testes unitários (3 pulados, os `@bloqueado`) e 14 de integração sem falhas, gate
  JaCoCo atendido.
- Mutação com a correção aplicada. Cada quebra derruba o teste reescrito correspondente; antes, os três passavam
  com qualquer código:

| Quebra | Teste que falhou |
|---|---|
| Aprovação sem evento `APROVACAO` | `rf27T1_transicaoDeEstadoDeReserva_deveGerarAuditoria` |
| Cadastro de sala sem auditoria | `rf28T1_alteracaoDeCadastro_deveGerarAuditoria` |
| Rejeição por sobreposição sem auditoria | `rf28T2_rejeicaoAutomaticaPorSobreposicao_deveGravarAcaoEspecifica` |

No CI (PR #12, run 43125582350, commit `d69901d`), `def011`, `rf27T1`, `rf28T1` e `rf28T2` estão verdes; a suíte
teve 398 casos, 0 falhas e 3 pulados.
