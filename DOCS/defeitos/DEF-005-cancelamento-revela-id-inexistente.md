# DEF-005 — Cancelamento de id inexistente responde 409 e revela quais ids existem

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 11/11/2026, commit desta correção) |
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

`ReservaService.cancelar` e `ReservaService.alterar` trocam a sequência "inexistente → 409; não é dono → 403" por uma
checagem única de posse (`ehDono`), em que reserva inexistente conta como "não é sua". O resultado é o mesmo `403`,
com a mesma mensagem, para id alheio e id inexistente. Em `alterar` a validação do intervalo continua antes da posse;
com intervalo inválido o id alheio e o inexistente recebem o mesmo `400`, então essa ordem não reabre o vazamento e
mantém `RegrasCriticasTest.rc01T6` como está. A rota `PUT` (DEF-004) tinha a mesma falha, também corrigida aqui.
Nenhum teste foi alterado.

## Reteste

Linha 5 (`PUT`) e linha 6 (cancelamento) da matriz: a célula "Solicitante, id inexistente → 403" está verde nas duas.
`Posse.idAlheioEIdInexistenteSaoIndistinguiveis` está verde: consultar, alterar e cancelar devolvem o mesmo `status`,
`title` e `detail` para id alheio e inexistente. Suíte completa sem falhas fora da matriz.
