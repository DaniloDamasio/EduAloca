# DEF-004 — Alteração de reserva não exposta: PUT responde 405 para todos

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 11/11/2026, commit desta correção) |
| Severidade | Alta: RF-13 inacessível pela API; anônimo recebe 405 em vez de 401 |
| Prioridade | Alta |
| Requisitos | RF-03, RF-13 |
| Componente | `ReservaController` |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `a8854c9` |
| Matriz | D-02, linha 5 |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `celulaDaMatriz` (linha 5, 4 células visíveis), `entradaInvalida` (2 casos), `Posse` (3 testes) |

## Resumo

Não há `@PutMapping("/reservas/{id}")`. A regra (intervalo, posse, estado, conflito) existe em
`ReservaService.alterar`, mas nenhuma rota a usa. O Spring responde 405 antes de autenticar.

## Passos para reproduzir

1. Como `usuario@example.com`, criar uma reserva (201).
2. `PUT /reservas/{id}` com `{"inicio":"2027-03-10T14:00","fim":"2027-03-10T16:00"}`: com o token do dono, com o token
   de `novo@example.com` e sem token.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

Dono `200`; outro Solicitante `403` (também para id inexistente); anônimo `401`; Responsável e Administrador `403`;
`fim` antes de `inicio` ou ausente `400`.

## Resultado obtido

```
linha 5 PUT /reservas/{id} como ANONIMO (PROPRIA): ... ==> expected: <401> but was: <405>
linha 5 PUT /reservas/{id} como SOLICITANTE (PROPRIA): ... ==> expected: <200> but was: <405>
linha 5 PUT /reservas/{id} como OUTRO_SOLICITANTE (PROPRIA): ... ==> expected: <403> but was: <405>
linha 5 PUT /reservas/{id} como SOLICITANTE (INEXISTENTE): ... ==> expected: <403> but was: <405>
alterar reserva com fim antes do inicio: ... ==> expected: <400> but was: <405>
```

## Causa raiz

Rota não implementada em [ReservaController.java](../../src/main/java/com/edualoca/api/reserva/ReservaController.java).

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

`PUT /reservas/{id}` em `ReservaController`, com corpo `AlterarReservaRequisicao(inicio, fim)` e o usuário do token
como autor. A regra continua toda em `ReservaService.alterar` (intervalo, posse, estado e conflito); o controller não
decide nada. Como o `@UsuarioAutenticado` vem antes do `@RequestBody`, anônimo recebe 401 antes de qualquer validação
do corpo. Nenhum teste foi alterado.

## Reteste

Linha 5 da matriz: 5 de 6 células verdes (anônimo 401, dono 200, outro Solicitante 403, Responsável 403,
Administrador 403). A célula "id inexistente → 403" continua vermelha (409), pela mesma causa do DEF-005, e fica com
ele. `entradaInvalida`: "alterar com fim antes do inicio" e "alterar sem fim" verdes (400 citando `fim`).
`Posse.alterarReservaAlheia` verde: 403 e a reserva do dono segue em 10/03 08:00-10:00, `APROVADA`.
