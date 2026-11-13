# DEF-017 — Solicitante do seed perde a posse das reservas depois de um reinício (modo PostgreSQL)

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 13/11/2026; red `010298b`) |
| Severidade | Alta: depois de reiniciar com PostgreSQL, o Solicitante não vê nem cancela as próprias reservas (RF-03, RF-14) |
| Prioridade | Alta: introduzido pela recarga de reservas da branch `persistencia-catalogo`, antes do merge |
| Requisitos | RF-03, RF-13, RF-14 |
| Componente | `Banco.seedSeNecessario` × `AutenticacaoService.autenticar` |
| Encontrado em | 13/11/2026, na análise do que ainda ficava em memória, antes do merge do PR #17 |
| Teste que detecta | `PersistenciaReinicioIT.solicitanteDoSeedContinuaDonoDepoisDoReinicio` |

## Resumo

As contas de Solicitante do seed (`usuario@example.com`, `novo@example.com`) não tinham id fixo: o login fazia
`usuarioIdPorEmail.computeIfAbsent(email, e -> UUID.randomUUID())`, então cada subida da aplicação sorteava um id novo.
Enquanto as reservas não sobreviviam ao reinício, isso não aparecia. Com a recarga das reservas do PostgreSQL, a reserva
volta com o `solicitanteId` antigo e o login devolve outro id: "Minhas reservas" fica vazia e cancelar responde 403.

## Passos para reproduzir

1. Subir com PostgreSQL, entrar como `usuario@example.com` e criar uma reserva.
2. Reiniciar a aplicação e entrar de novo com a mesma conta.
3. Abrir "Minhas reservas".

## Resultado esperado

A reserva aparece e pode ser cancelada pelo dono.

## Resultado obtido

```
o id da conta não pode mudar a cada subida ==> expected: <b974110e-…> but was: <c99efb6c-…>
```

## Causa raiz e correção

Responsável e Administrador do seed já tinham ids fixos (`RESPONSAVEL_BLOCO_A_ID`, `ADMINISTRADOR_ID`); as contas de
Solicitante não. Correção: `Banco.SOLICITANTE_SEED_ID` e `SOLICITANTE_2_SEED_ID`, registrados no seed.

## Reteste

`PersistenciaReinicioIT` 2/2 (o teste novo faz login real nas duas subidas, lista e cancela a reserva). `./mvnw -B
verify`: 573 unitários e 24 ITs, 0 falhas.

## Limite

Contas criadas pelo Administrador continuam em memória (ADR-010): somem no reinício, junto com o vínculo das reservas
delas. Usuários de carga (`SeedUsuariosPerf`, perfil `perf`) também recebem id na primeira autenticação de cada subida.
