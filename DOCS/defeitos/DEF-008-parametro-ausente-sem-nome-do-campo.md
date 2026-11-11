# DEF-008 — Parâmetro obrigatório ausente: a mensagem não diz qual campo falta

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 11/11/2026, commit desta correção) |
| Severidade | Baixa: o usuário recebe uma mensagem genérica sobre "campos e datas" e não sabe o que corrigir |
| Prioridade | Baixa |
| Requisitos | RF-38, RNF-04 |
| Componente | `TratadorDeErros.entradaInvalida` |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `a8854c9` |
| Matriz | Fora da matriz (validação de entrada) |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `entradaInvalida` ("buscar salas sem inicio") |

## Resumo

Sem o parâmetro `inicio`, `GET /salas/disponiveis` responde `400` com o texto fixo "Requisicao invalida: verifique os
campos e o formato das datas (aaaa-mm-ddThh:mm)". O RF-38 pede mensagem compreensível; a mesma API já nomeia o campo
nos demais casos (finalidade, motivo, justificativa).

## Passos para reproduzir

`GET /salas/disponiveis?fim=2027-03-10T10:00` com token de Solicitante.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

`400` com mensagem que cite `inicio`.

## Resultado obtido

```
{"mensagem":"Requisicao invalida: verifique os campos e o formato das datas (aaaa-mm-ddThh:mm)"}
```

(hoje o caso falha primeiro pelo formato, DEF-002; o texto acima é o `detail` que ficaria.)

## Causa raiz

`MissingServletRequestParameterException` e `MethodArgumentTypeMismatchException` caem no mesmo handler genérico, que
descarta o nome do parâmetro. O nome do parâmetro é parte do contrato público da API, não detalhe interno.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

`TratadorDeErros` separa o handler genérico em três: corpo ilegível (mensagem genérica de antes), parâmetro ausente
("Parametro obrigatorio ausente: informe 'inicio'") e parâmetro com tipo inválido ("Valor invalido para
'capacidadeMinima': ..."). O nome do parâmetro é o mesmo que o cliente usa na URL, ou seja, contrato público e não
detalhe interno. O valor recebido **não** é ecoado, para não refletir entrada do cliente na resposta. Nenhum teste foi
alterado.

## Reteste

`entradaInvalida`: 19/19 verdes ("buscar salas sem inicio" agora cita `inicio`). `ApiSegurancaTest.buscaSemParametro`
e os casos de data malformada continuam verdes (400, sem `Exception` no corpo).
