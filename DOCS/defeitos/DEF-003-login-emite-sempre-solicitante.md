# DEF-003 — Login emite sempre o perfil Solicitante

| Campo | Valor |
|---|---|
| Status | **Aberto** |
| Severidade | Alta: nenhum usuário chega a Responsável ou Administrador pela API; aprovação, histórico, manutenção e relatório só funcionam em teste |
| Prioridade | Alta |
| Requisitos | RF-01, RF-02, RF-19, RF-21, RF-29, RF-36 |
| Componente | `AutenticacaoService`, `Banco` (seed de credenciais) |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `a8854c9` |
| Matriz | D-01, linha 1, e todas as células de Responsável e Administrador (linhas 2 a 12) |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `celulaDaMatriz` (26 células), `entradaInvalida` (5 casos) |

## Resumo

`AutenticacaoService.autenticar` devolve `Perfil.SOLICITANTE` para qualquer credencial válida, e o seed só tem contas
de Solicitante (`usuario@example.com`, `novo@example.com`). Não existe conta de Responsável nem de Administrador, e
mesmo se existisse o token sairia como Solicitante.

## Passos para reproduzir

1. `POST /auth/login` com `{"email":"responsavel@example.com","senha":"senhaResponsavel123"}`.
2. Para comparar: logar como `usuario@example.com` e chamar `GET /relatorios/utilizacao` com o token (403, porque o
   token é de Solicitante).

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

Login de cada conta responde `200`, e o token tem o perfil cadastrado da conta: Responsável chega a `200` no histórico
e no relatório; Administrador chega a `201` na manutenção.

## Resultado obtido

```
linha 1 POST /auth/login como RESPONSAVEL (NENHUM): corpo {"mensagem":"Credenciais invalidas"}
  ==> expected: <200> but was: <401>
login de ADMINISTRADOR (admin@example.com) falhou, sem token real para o perfil
```

As 26 células de Responsável/Administrador falham no login, antes de exercitar a operação. Por isso as células das
linhas 3 (D-03) e 7/8 (D-05) **ainda não mostram a divergência própria**; elas serão reavaliadas depois desta correção.

## Causa raiz

[AutenticacaoService.java:17](../../src/main/java/com/edualoca/domain/usuario/AutenticacaoService.java#L17) fixa
`Perfil.SOLICITANTE`; `Banco` guarda só e-mail→senha, sem perfil.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

Pendente.

## Reteste

Pendente.
