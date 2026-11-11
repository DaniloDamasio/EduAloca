# DEF-002 — Respostas de erro fora do padrão ProblemDetail

| Campo | Valor |
|---|---|
| Status | **Aberto** |
| Severidade | Média: o conteúdo já é seguro (sem stack trace), mas o contrato de erro não é o padrão (RFC 9457) e não informa `status`/`title` no corpo |
| Prioridade | Alta: afeta toda resposta 4xx/5xx da API |
| Requisitos | RF-38, RNF-06 |
| Componente | `TratadorDeErros`, `ErroResposta` |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `a8854c9` |
| Matriz | Todas as células de erro (401, 403, 405, 409) |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `celulaDeErroEmProblemDetail` (35 de 35), `entradaInvalida` (as 19), `Posse` |

## Resumo

Todo erro sai como `application/json` com o corpo `{"mensagem": "..."}`. O cliente não recebe
`application/problem+json` nem os campos `type`, `title`, `status` e `detail` do ProblemDetail, e precisa conhecer o
formato próprio da API para tratar qualquer erro.

## Passos para reproduzir

1. `GET /reservas/{id}` sem cabeçalho `Authorization`.
2. Observar o `Content-Type` e o corpo da resposta.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

`401` com `Content-Type: application/problem+json` e corpo com `status: 401`, `title` e `detail` preenchidos, sem
stack trace, SQL ou nome de classe interna.

## Resultado obtido

```
erro 401 deve vir como application/problem+json, veio application/json:
  {"mensagem":"Autenticacao necessaria: informe um token de acesso valido"}
```

Igual nos 35 casos de erro da matriz e nos 19 de validação. A checagem de conteúdo (nada de `Exception`,
`com.edualoca`, `java.`, SQL) **passa** hoje: o defeito é de formato, não de vazamento.

## Causa raiz

`TratadorDeErros` monta `ResponseEntity<ErroResposta>` à mão em vez de devolver `ProblemDetail`.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

Pendente.

## Reteste

Pendente.
