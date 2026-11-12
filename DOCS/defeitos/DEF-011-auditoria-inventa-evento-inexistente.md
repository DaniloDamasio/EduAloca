# DEF-011 — Consulta de auditoria inventa evento para entidade sem histórico

| Campo | Valor |
|---|---|
| Status | **Aberto** |
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

_Pendente._

## Reteste

_Pendente._
