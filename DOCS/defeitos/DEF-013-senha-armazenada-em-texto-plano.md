# DEF-013 — Senha armazenada em texto plano

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 11/11/2026, commit desta correção; red `3835d8b`) |
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

## Correção

- `SenhaHasher.gerar`: PBKDF2-HMAC-SHA256 do JDK (`PBKDF2WithHmacSHA256`), 600.000 iterações, sal de 16 bytes de
  `SecureRandom` por senha e chave de 256 bits. Formato armazenado: `pbkdf2-sha256$iteracoes$sal$hash` (Base64).
  Sem dependência nova.
- `SenhaHasher.confere`: compara com `MessageDigest.isEqual` (tempo constante). Conta inexistente, senha vazia ou
  valor mal formado conferem contra um hash fictício, com o mesmo custo, e devolvem `false`. Assim o tempo de
  resposta não revela se o e-mail existe.
- `Banco.credenciais` guarda só o hash. Novo `Banco.registrarCredencial(email, senha)`, usado por
  `SeedUsuariosPerf`. Os hashes das contas de seed são calculados uma vez por JVM (`Banco.HashesSeed`), para que
  `resetParaTeste()` não pague 5 × ~50 ms a cada teste.
- `AutenticacaoService.autenticar` usa `confere` e devolve no `Usuario` o valor armazenado. O SHA-256 sem sal saiu.
- `SeedUsuariosPerfTest` deixou de exigir a senha em claro no mapa e confere o trim pelo login.

## Reteste

- `ArmazenamentoSenhaTest`: 4 de 4 verdes (antes: 3 falhas).
- `./mvnw -B verify` local (com Docker): 394 unitários (3 pulados, os `@bloqueado`) e 14 ITs sem falhas, gate
  JaCoCo atendido. A suíte passou de ~15 s para ~30 s por causa do custo do hash nos logins dos testes de API.
- Mutação sobre a correção:

| Quebra | Teste que falhou |
|---|---|
| Sal fixo (sem `nextBytes`) | `mesmaSenha_geraValoresArmazenadosDiferentes` |
| `confere` sempre `true` | `loginContinuaFuncionando` |
| `confere` ignora o resultado da comparação | `loginContinuaFuncionando` |

## Limite

- As contas de demonstração continuam com senha fixa no código (`Banco.HashesSeed`). É massa de teste e
  demonstração: em implantação real, as contas devem vir de cadastro ou de segredo externo.
- Não há limite de tentativas nem bloqueio de login (força bruta). Isso não está nos requisitos e fica como risco
  residual.
- Com o perfil `perf`, a subida calcula 50 hashes (~2,5 s) e cada login custa ~50 ms de CPU. A rodada 01 de
  desempenho foi medida **antes** desta correção, mas só tinha 50 logins, todos no ramp-up.

## Por que nenhum teste nem o SonarCloud pegou

O SonarCloud reporta 0 vulnerabilidades: guardar `String` em `Map` não casa com nenhuma regra. O único teste do
RNF-05 conferia o campo errado.
