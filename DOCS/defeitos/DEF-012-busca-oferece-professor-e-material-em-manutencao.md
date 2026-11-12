# DEF-012 — Busca oferece professor e material em manutenção

| Campo | Valor |
|---|---|
| Status | **Aberto** (red registrado; correção em andamento) |
| Severidade | Média: a busca oferece recurso indisponível (contra o RF-11), mas a criação da reserva continua recusando com 409 (mutante M5 morto), então não há reserva indevida |
| Prioridade | Média |
| Requisitos | RF-11, RF-22 |
| Componente | `PesquisaRecursoService.disponivel` / `buscarMateriais` |
| Encontrado em | 11/11/2026, durante a [mutação manual](../testes/mutacao-manual.md) §2.4 (leitura de código), commit-base `ee540c5`; confirmado em `a670c68` pelo [relatório de qualidade](../relatorio-qualidade.md) (A-13) |
| Teste que detecta | `BuscaRecursoEmManutencaoTest` :: `professorEmManutencao_naoApareceNaBusca`, `materialEmManutencao_naoApareceNaBusca` |

## Resumo

`disponivel()` só consulta manutenção quando o recurso é `Sala`. `buscarProfessores` usa `disponivel()`, e por isso
ignora a manutenção (afastamento) do professor. `buscarMateriais` nem chama `disponivel()`: só olha a quantidade
livre. O Solicitante vê o recurso como disponível e recebe 409 ao tentar reservar.

## Passos para reproduzir

1. Cadastrar dois professores com a mesma competência e uma manutenção para um deles cobrindo o intervalo.
2. `buscarProfessores(competencia, inicio, fim)`.
3. Repetir com dois materiais do mesmo tipo e `buscarMateriais`.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=BuscaRecursoEmManutencaoTest
```

## Resultado esperado

Só o recurso sem manutenção no intervalo. Manutenção que só encosta no intervalo (semiaberto) não bloqueia.

## Resultado obtido

```
BuscaRecursoEmManutencaoTest.professorEmManutencao_naoApareceNaBusca:41  Expecting actual: ... but some elements were not expected
BuscaRecursoEmManutencaoTest.materialEmManutencao_naoApareceNaBusca:54   Expecting actual: ... but some elements were not expected
```

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- Teste de unidade sobre `CadastroRecursoService` e `PesquisaRecursoService`

## Limite

A API HTTP só expõe `/salas/disponiveis`; o defeito aparece pelo serviço de domínio, não por uma rota.
