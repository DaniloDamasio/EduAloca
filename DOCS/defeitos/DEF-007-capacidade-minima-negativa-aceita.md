# DEF-007 — Busca de salas aceita capacidade mínima negativa

| Campo | Valor |
|---|---|
| Status | **Aberto** |
| Severidade | Baixa: entrada fora do domínio é aceita silenciosamente e devolve todas as salas |
| Prioridade | Média |
| Requisitos | RNF-04, RF-08 |
| Componente | `SalaController`, `PesquisaRecursoService.buscarSalas` |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `a8854c9` |
| Matriz | Fora da matriz (validação de entrada) |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `entradaInvalida` ("buscar salas com capacidade minima negativa") |

## Resumo

`GET /salas/disponiveis?capacidadeMinima=-5` responde `200` com todas as salas livres. Capacidade negativa não tem
significado; o RNF-04 pede `400` para limite inválido antes da regra de negócio.

## Passos para reproduzir

`GET /salas/disponiveis?inicio=2027-03-10T08:00&fim=2027-03-10T10:00&capacidadeMinima=-5` com token de Solicitante.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

`400` com mensagem que cite a capacidade.

## Resultado obtido

```
buscar salas com capacidade minima negativa: corpo [{"id":"00000000-0000-0000-0000-000000000001",...}]
  ==> expected: <400> but was: <200>
```

## Causa raiz

Nenhuma checagem de limite em `capacidadeMinima`; o filtro `capacidade >= -5` aceita tudo.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

Pendente.

## Reteste

Pendente.
