# DEF-003 — Login emite sempre o perfil Solicitante

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 11/11/2026, commit desta correção) |
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

`Banco` ganha `perfilPorEmail` e o seed cria três contas com id fixo: `responsavel@example.com` (Responsável do
Bloco A), `responsavel.b@example.com` (Responsável do Bloco B) e `admin@example.com` (Administrador).
`responsavelPorLocalizacao` passa a apontar para essas contas, e não para um UUID aleatório.
`AutenticacaoService.autenticar` emite o perfil da conta; conta sem perfil cadastrado (as duas de Solicitante e as do
perfil `perf`) continua Solicitante. Nenhum teste foi alterado.

Limitação mantida (fora do escopo deste defeito, matriz §7): as senhas do seed seguem em texto plano, contra o RNF-05.

## Reteste

Todas as células de Responsável e Administrador passam a obter token pelo login (linha 1: 4/4 verdes). Com o token
real, as células das linhas 2, 4, 9, 11 e 12 ficaram verdes para os dois perfis. Ficaram expostas as divergências que
o login mascarava: linha 3 (D-03, Responsável/Administrador criam reserva com 201) e linhas 7/8 (D-05, Responsável do
Bloco B decide reserva do Bloco A), registradas em DEF-009 e DEF-010. Suíte completa sem nenhuma outra falha.
