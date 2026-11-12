# DEF-013 — Senha armazenada em texto plano

| Campo | Valor |
|---|---|
| Status | **Aberto** (red registrado; correção em andamento) |
| Severidade | Alta: quem lê o armazenamento de credenciais (dump de memória, log de depuração, futura tabela) obtém todas as senhas em claro, contra o RNF-05 (Crítico) |
| Prioridade | Alta |
| Requisitos | RNF-05, RF-01 |
| Componente | `Banco.credenciais`, `AutenticacaoService.autenticar`, `SenhaHasher`, `SeedUsuariosPerf` |
| Encontrado em | 11/11/2026, [relatório de qualidade](../relatorio-qualidade.md) (A-11), por inspeção de código no commit `a670c68` |
| Teste que detecta | `ArmazenamentoSenhaTest` :: `credenciaisDeSeed_naoFicamEmClaroNemComHashSemSal`, `mesmaSenha_geraValoresArmazenadosDiferentes`, `usuarioAutenticado_naoCarregaSenhaEmClaro` |

## Resumo

`Banco.credenciais` é um mapa de e-mail para **senha em claro**, preenchido pelo seed e por `SeedUsuariosPerf`.
`AutenticacaoService.autenticar` compara com `String.equals` (sem tempo constante) e só depois calcula
`SenhaHasher.hash`, um SHA-256 **sem sal** e rápido, para preencher `Usuario.senhaHash`. O teste
`RequisitosNaoFuncionaisTest.rnf05T1` passava porque conferia só esse valor derivado, e não o que fica armazenado.
O `SeedUsuariosPerfTest` chegava a exigir a senha em claro no mapa.

## Passos para reproduzir

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=ArmazenamentoSenhaTest
```

## Resultado esperado

O valor armazenado não é a senha, não a contém e não é o SHA-256 dela. Duas contas com a mesma senha guardam valores
diferentes (sal por conta). O login certo continua autenticando, e o errado continua respondendo 401 com a mesma
mensagem para e-mail inexistente.

## Resultado obtido

```
ArmazenamentoSenhaTest.credenciaisDeSeed_naoFicamEmClaroNemComHashSemSal:59 [credencial de admin@example.com]
ArmazenamentoSenhaTest.mesmaSenha_geraValoresArmazenadosDiferentes:76
ArmazenamentoSenhaTest.usuarioAutenticado_naoCarregaSenhaEmClaro:103
```

`loginContinuaFuncionando` passa antes e depois: é a rede de segurança da correção.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- Teste de unidade sobre `Banco`, `AutenticacaoService` e `SeedUsuariosPerf`

## Por que nenhum teste nem o SonarCloud pegou

O SonarCloud reporta 0 vulnerabilidades: guardar `String` em `Map` não casa com nenhuma regra. O único teste do
RNF-05 conferia o campo errado.
