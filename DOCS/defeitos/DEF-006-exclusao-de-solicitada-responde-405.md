# DEF-006 — DELETE de reserva em SOLICITADA responde 405 numa rota mapeada

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 11/11/2026, commit desta correção) |
| Severidade | Baixa: o acesso está correto (ninguém apaga), mas o status contradiz o significado do código |
| Prioridade | Baixa |
| Requisitos | RF-30 (regra P-3 da matriz) |
| Componente | `ReservaService.excluir` |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `a8854c9` |
| Matriz | D-06, linha 10 |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `celulaDaMatriz` (linha 10, SOLICITANTE/RESTRITA) |

## Resumo

`DELETE /reservas/{id}` existe e autentica. Para reserva em `SOLICITADA` o serviço lança `405`, que significa "método
não suportado por este recurso" e é o que o Spring devolve para rota sem o método. Para os demais estados, e para id
inexistente, responde `403`. A matriz pede `403` para qualquer perfil autenticado.

## Passos para reproduzir

1. Como `usuario@example.com`, criar reserva de sala restrita (nasce `SOLICITADA`).
2. `DELETE /reservas/{id}` com o mesmo token.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

`403`, igual a reserva iniciada e a id inexistente.

## Resultado obtido

```
linha 10 DELETE /reservas/{id} como SOLICITANTE (RESTRITA):
  corpo {"mensagem":"Exclusao fisica de reserva nao e exposta pela API"} ==> expected: <403> but was: <405>
```

## Causa raiz

[ReservaService.java:200-207](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L200-L207). O
`AuditoriaTest.rf30T2` fixou o 405 quando a pergunta Q22 estava aberta; a matriz (P-3) resolveu a favor de 403.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

`ReservaService.excluir` responde sempre `403` ("Reservas nao podem ser excluidas; para encerrar uma reserva,
cancele-a"), em qualquer estado e para qualquer perfil, sem consultar a reserva, para que a resposta também não revele
o estado nem se o id existe.

**Teste alterado:** `AuditoriaTest.rf30T2_deleteDeReservaSolicitada…` esperava `405`. Esse valor foi fixado quando a
pergunta Q22 estava aberta ("assumindo que nenhum DELETE é exposto"); a matriz (P-3) resolveu a Q22 a favor de `403`,
que o RF-30 já aceitava ("403/405"). A asserção passou para `403` e o `@DisplayName` cita a resolução. O cenário BDD
"Reserva iniciada não pode ser apagada" aceita 403 ou 405 e não mudou.

## Reteste

Linha 10 da matriz: 5/5 células verdes (anônimo 401; Solicitante em `SOLICITADA` e em `APROVADA`, Responsável e
Administrador 403). Antes da correção, Responsável e Administrador também recebiam 405 em `SOLICITADA` (visível só
depois do DEF-003). Suíte completa sem falhas fora da matriz.
