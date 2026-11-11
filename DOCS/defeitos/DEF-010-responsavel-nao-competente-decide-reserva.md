# DEF-010 — Responsável de outro bloco aprova e rejeita reserva restrita

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 11/11/2026, commit desta correção) |
| Severidade | Alta: qualquer Responsável decide reserva de recurso que não é seu, contra a regra crítica “Somente responsáveis aprovam recursos restritos” lida como Responsável competente |
| Prioridade | Alta |
| Requisitos | RF-18, RF-19, RF-32 (regra P-2 da matriz) |
| Componente | `ReservaService.aprovar` / `rejeitar` |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `9c71028` (visível só depois do DEF-003) |
| Matriz | D-05, linhas 7 e 8 |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `celulaDaMatriz` (linhas 7 e 8, RESPONSAVEL_NAO_COMPETENTE) |

## Resumo

A aprovação e a rejeição checam só o perfil (Responsável ou Administrador). O vínculo Responsável↔localização existe em
`Banco.responsavelPorLocalizacao`, mas só a notificação (RF-32) o usa. O Responsável do Bloco B aprova reserva de
laboratório do Bloco A. O Administrador continua podendo decidir qualquer reserva (RF-19, decisão de 11/11/2026).

## Passos para reproduzir

1. Como `usuario@example.com`, reservar o "Lab Restrito" (Bloco A, restrito): nasce `SOLICITADA`.
2. Como `responsavel.b@example.com` (Bloco B), `POST /reservas/{id}/aprovacao`.
3. Repetir com `POST /reservas/{id}/rejeicao` e `{"justificativa":"laboratorio reservado para banca"}`.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

`403` para o Responsável do Bloco B; `200` para o Responsável do Bloco A e para o Administrador.

## Resultado obtido

```
linha 7 POST /reservas/{id}/aprovacao como RESPONSAVEL_NAO_COMPETENTE (RESTRITA):
  corpo {"id":"...","estado":"APROVADA",...} ==> expected: <403> but was: <200>
linha 8 POST /reservas/{id}/rejeicao como RESPONSAVEL_NAO_COMPETENTE (RESTRITA):
  corpo {"id":"...","estado":"REJEITADA",...} ==> expected: <403> but was: <200>
```

## Causa raiz

[ReservaService.java:141-170](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L141-L170): checagem
de perfil sem checagem de competência.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

`ReservaService.aprovar` e `rejeitar` chamam `exigirDecisorCompetente` depois de carregar a reserva e **antes** de
checar o estado, para que um Responsável não competente não descubra o estado dela:

- Administrador: decide qualquer reserva (RF-19, decisão de 11/11/2026).
- Responsável: decide se `Banco.responsavelPorLocalizacao` aponta para ele em alguma sala da reserva; senão `403`
  ("Apenas o Responsavel pelo local do recurso (ou o Administrador) pode aprovar esta reserva").

**Lacuna conhecida (não corrigida):** material e professor não têm localização nem responsável. Reserva **sem nenhuma
sala** continua decidida por qualquer Responsável, como antes. Fechar isso exige modelar o vínculo responsável↔recurso
(material e professor), o que muda o modelo de dados e fica fora deste defeito.

**Testes alterados (só o arranjo, nenhuma asserção):** os testes de aprovação e rejeição usavam um Responsável com UUID
aleatório, sem vínculo com bloco nenhum. Com a regra P-2 esse Responsável deixou de ser competente. Cada um passa a usar
o id do Responsável de seed do bloco da sala que o teste já usava: `Banco.RESPONSAVEL_BLOCO_A_ID` em
`AprovacaoRecursosRestritosTest` e `MaquinaEstadosReservaTest` (salas no Bloco A); `Banco.RESPONSAVEL_BLOCO_B_ID` em
`BddAprovacaoBloqueioTest`, `HistoricoAuditavelReservaTest`, `HistoricoReservaApiTest`, nos passos Cucumber
`AprovacaoBloqueioSteps` e em `HistoricoReservaTransacaoIT` (salas no Bloco B). As asserções, os cenários `.feature` e o BDD-R1-05 ("Administrador aprova")
não mudaram.

## Reteste

`MatrizAutorizacaoApiTest`: 114/114 verdes. Linhas 7 e 8: Responsável do Bloco A 200, Responsável do Bloco B 403,
Administrador 200, Solicitante 403, anônimo 401. Suíte completa (`./mvnw test`): 374 testes, 0 falhas, 3 ignorados
(os mesmos 3 ignorados de antes desta série de correções). Esse reteste rodou só o Surefire. No `./mvnw -B verify` completo
(com Docker para os ITs), `HistoricoReservaTransacaoIT` falhou em 2 casos pelo mesmo motivo (Responsável com UUID
aleatório) e recebeu o mesmo ajuste de arranjo em commit seguinte. Depois disso: `./mvnw -B verify` **BUILD SUCCESS**,
374 testes unitários (3 ignorados) e 14 ITs verdes, gate JaCoCo atendido. Antes do ajuste de arranjo, 26 testes antigos falhavam com
`403`, o que confirma que a regra nova é aplicada também pelo domínio, não só pela API.
