# DEF-014 — Retirada não vincula o item; devolução reabre ciclo encerrado

| Campo | Valor |
|---|---|
| Status | **Aberto** (red registrado; correção em andamento) |
| Severidade | Alta: o RF-24 (Crítico) pede que 100% das retiradas vinculem reserva + **item** + autor + instante, e o item não é guardado; não dá para saber qual material saiu nem cobrar a devolução certa |
| Prioridade | Alta |
| Requisitos | RF-24, RF-25, RF-26 |
| Componente | `RetiradaMaterialService.registrarRetirada` / `registrarDevolucao`, `RegistroRetirada` |
| Encontrado em | 11/11/2026, auditoria por mutação de RF-24/25 ([mutacao-manual.md §6](../testes/mutacao-manual.md)), commit `11ba3b3` |
| Teste que detecta | `RetiradaVinculoItemTest` (6 casos) |

## Resumo

1. `registrarRetirada(autor, reservaId, itemId, dataHora)` recebe `itemId` e o **descarta**: `RegistroRetirada` não
   tem campo de item.
2. O ciclo aberto é checado por reserva, não por item. Numa reserva com projetor e caixa de som, a retirada da
   caixa é recusada com 409 enquanto o projetor estiver fora.
3. Qualquer `itemId`, inclusive um que não está na reserva, é aceito.
4. `registrarDevolucao` aceita devolver um registro **já devolvido** e sobrescreve data e condição. Um item
   devolvido `DANIFICADO` pode virar `INTEGRO` numa segunda chamada.

Os testes existentes (`RetiradaDevolucaoMaterialTest`) passavam porque conferiam só `getReservaId()`, e `rf24T2`
usava uma reserva inexistente, de modo que a regra "só `EM_USO`" nunca era exercitada (mutantes R1, R3 e R4
sobreviviam).

## Passos para reproduzir

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=RetiradaVinculoItemTest
```

## Resultado esperado

- O registro guarda reserva, item, autor e instante informados.
- Item que não é material da reserva: 409.
- Dois materiais da mesma reserva: cada um no seu ciclo. O mesmo material com ciclo aberto: 409.
- Devolução de ciclo já encerrado: 409, sem alterar a devolução registrada.

## Resultado obtido

Falha de compilação: `The method getItemId() is undefined for the type RegistroRetirada`. O registro não tem onde
guardar o item.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- Teste de unidade sobre `ReservaService` e `RetiradaMaterialService`

## Premissa

"Material vinculado a reserva" (RF-24) foi lido como "o item é um `Material` entre os recursos da reserva". O código
de recusa para item fora da reserva segue o 409 já usado para estado incompatível (gap Q17).
