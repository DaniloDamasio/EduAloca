# DEF-005 — Cancelamento de id inexistente responde 409 e revela quais ids existem

| Campo | Valor |
|---|---|
| Status | **Aberto** |
| Severidade | Média: enumeração de ids de reserva por qualquer Solicitante (403 = existe e é de outro; 409 = não existe) |
| Prioridade | Média |
| Requisitos | RF-03, RF-14 (regra P-1 da matriz) |
| Componente | `ReservaService.cancelar` |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `a8854c9` |
| Matriz | Linha 6, célula "403 alheia" com id inexistente. **Não estava listada** na seção 4 da matriz (linha 6 marcada ✅) |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `celulaDaMatriz` (linha 6, SOLICITANTE/INEXISTENTE), `Posse.idAlheioEIdInexistenteSaoIndistinguiveis` |

## Resumo

A regra P-1 diz que o Solicitante recebe 403 em reserva de terceiro **inclusive quando o id não existe**. A consulta
(linha 4) já faz isso; o cancelamento testa a existência antes da posse e responde `409 Reserva inexistente`. Comparando
as duas respostas, um Solicitante descobre quais ids existem.

## Passos para reproduzir

1. Como `usuario@example.com`, `POST /reservas/{uuid-aleatorio}/cancelamento` com `{"motivo":"aula remarcada"}`.
2. Como `novo@example.com`, cancelar uma reserva real de `usuario@example.com`.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

`403` nos dois casos, com o mesmo corpo.

## Resultado obtido

```
linha 6 POST /reservas/{id}/cancelamento como SOLICITANTE (INEXISTENTE): corpo {"mensagem":"Reserva inexistente"}
  ==> expected: <403> but was: <409>
```

## Causa raiz

[ReservaService.java:125-131](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L125-L131): o `409`
de inexistência vem antes da checagem de posse. `alterar` tem a mesma ordem (linhas 86-92) e herdaria o defeito quando
a rota existir (DEF-004).

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

Pendente.

## Reteste

Pendente.
