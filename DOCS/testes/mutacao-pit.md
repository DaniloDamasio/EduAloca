# Mutação automatizada (PIT) das regras de domínio

Complementa a [mutação manual](mutacao-manual.md), que escolheu 77 mutantes nas regras críticas. Aqui o PIT gera os
mutantes sozinho, em todo o pacote `com.edualoca.domain`, e cada um é testado contra a suíte inteira, menos os ITs.
O objetivo é medir se os testes **verificam** o comportamento, e não só executam o código: cobertura alta, sozinha, não
garante isso.

## Como rodar

```
./mvnw -B -Pmutacao test-compile org.pitest:pitest-maven:mutationCoverage
```

Relatório em `target/pit-reports/index.html` (HTML) e `mutations.xml`. O perfil `mutacao` fica fora do `verify` e do
CI, porque cada rodada leva cerca de 5 minutos.

| Configuração | Valor |
|---|---|
| Ferramenta | PIT 1.19.1 com `pitest-junit5-plugin` 1.2.2 (`pom.xml`, perfil `mutacao`) |
| Classes mutadas | `com.edualoca.domain.*`, menos `Banco` (seed e estado em memória, não regra de negócio) |
| Testes | todos de `com.edualoca.*`, menos `*IT` (Testcontainers e navegador). Entram unitários, parametrizados, API por MockMvc e BDD |
| Mutadores | `DEFAULTS` (condicionais, limites, retornos, chamadas removidas, matemática, incremento) |
| Ambiente | Apple M4 Pro, JDK 21, 4 threads, 12/11/2026 |

## Resultado

| Rodada | O que mudou antes | Mortos / gerados | Escore | Força dos testes¹ | Cobertura de linha² |
|---|---|---|---|---|---|
| 1 | suíte da entrega (após os 4 testes fracos corrigidos) | 540 / 638 | 84,6% | 86,5% | 97% |
| 2 | primeira parte do `LacunasMutacaoPitTest` (reserva, busca, retirada) | 556 / 637 | 87,3% | 89,2% | 97% |
| 3 | segunda parte (bordas de usuário, colisão de manutenção, hash malformado, auditoria) | 574 / 637 | 90,1% | 91,1% | 98% |
| **4** | código morto removido; catálogo passa a usar `listarSalasAtivas`; teste de filtro da auditoria fortalecido | **577 / 637** | **90,6%** | **91,4%** | **98%** |

¹ Mortos sobre os mutantes que algum teste executa (sem os 6 sem cobertura). ² Linhas das classes mutadas.

**Por classe (rodada 4):** `ReservaService` 168/185, `CadastroRecursoService` 79/96, `PesquisaRecursoService` 55/58,
`UsuarioService` 54/59, `RetiradaMaterialService` 34/41, `SenhaHasher` 25/29, `RelatorioService` 24/25,
`AutenticacaoService` 18/20, `AuditoriaService` 16/18, `ReservaRepositorioEmMemoria` 14/14, `NotificacaoService` 13/13.
As entidades e valores (`Reserva`, `Sala`, `Material`, `Professor`, `Manutencao`, `EstadoReserva`, `Intervalos`,
`RegistroRetirada`, `Transacao` e outros) ficam com 100%.

## Os 60 mutantes que sobraram

| Grupo | Qtd. | Exemplo | Por que fica |
|---|---|---|---|
| Remoção de `Banco.seedSeNecessario()` | 37 | `ReservaService.cancelar`, `RetiradaMaterialService.buscar` | **Equivalente nos testes:** a suíte semeia o banco antes de cada teste (`BancoResetExtension` e `@BeforeEach`), então tirar a chamada não muda nada observável. Na aplicação, a primeira requisição semeia o banco por qualquer outro caminho |
| `SenhaHasher.confere` devolve `false` direto em vez de `falhaComCusto()` | 3 | linhas 60, 66 e 69 | **Equivalente no resultado:** os dois devolvem `false`. A diferença é só de tempo (a derivação fictícia que iguala o tempo de conta existente e inexistente), e tempo constante não é medido por teste unitário (limite registrado no relatório de qualidade, A-19) |
| Proteção dupla | 3 | `ReservaService.rejeitar:303` (perfil), `UsuarioService.cadastrar:48` (e-mail nulo) | **Equivalente:** se a primeira checagem some, outra recusa igual (a competência por local barra o Solicitante; e-mail nulo cai na validação de formato) |
| Parte de condição composta já coberta por outra | 5 | `CadastroRecursoService.registrarManutencao:84`, `reservasQueColidemComManutencao:103`, `UsuarioService.cadastrar:58`, `RetiradaMaterialService.sinalizarPendencias:86`, `SenhaHasher.gerar:38` | O mutante troca uma das partes (`== null`) por `false`, e a parte seguinte da mesma condição recusa a mesma entrada |
| Bloqueio de login: reinício da janela ao fim do bloqueio | 2 | `AutenticacaoService.registrarFalha:61` | **Equivalente com as constantes atuais:** janela e bloqueio valem 15 min, então quando o bloqueio vence a janela também já venceu. A condição só faz diferença se os valores mudarem |
| Identidade e ordem dos locks | 4 | `ReservaService.identidadeDoRecurso`, comparador de `adquirirLocksOrdenados` | Mudam qual lock é usado ou em que ordem. Num teste de uma thread não há efeito; a proteção contra dupla reserva entre instâncias é a constraint do banco, coberta pelos ITs de concorrência |
| Desempate de ordenação | 2 | `ReservaService.listar` (id como desempate), `CadastroRecursoService.listarManutencoes` | Só atua com duas reservas ou manutenções no mesmo instante de início; sem efeito nos cenários dos testes |
| Filtro de capacidade mínima da busca de salas aceitando tudo | 1 | `PesquisaRecursoService.buscarSalas:23` | **Lacuna real**: nenhum teste de domínio combinava sala abaixo da capacidade pedida. Depois da rodada 4 entrou `LacunasMutacaoPitTest.filtroDeCapacidade` (borda 20/21); o efeito no escore não foi medido numa 5ª rodada |
| Consulta de salas ativas com usuário autenticado | 1 | `CadastroRecursoService.listarSalasAtivas(Usuario):203` (sem cobertura) | Sobrecarga usada só por um teste que confere o 401 sem usuário; o caminho autenticado é o da sobrecarga sem argumento, usada pelo catálogo |
| Getters | 2 | `AuditoriaEvento.getId`, `NotificacaoEnviada.getMensagem` | Acessores sem regra |

## O que a mutação encontrou

- **4 testes fracos** que passavam com qualquer código (`rnf05T1`, `rnf06T1`, `rnf09T1`, `rc03T4`), já apontados pela
  auditoria de 10/11, foram reescritos antes da rodada 1. O `rc03T4` passou a injetar falha de gravação, e um mutante
  manual que deixa o lock preso faz o teste falhar por timeout.
- **37 mutantes a mais mortos** da rodada 1 para a 4, por `LacunasMutacaoPitTest` (23 casos executados hoje, contando os parametrizados):
  - alterar reserva grava auditoria e libera o lock;
  - a reserva não conflita consigo mesma ao ser alterada;
  - cancelar nos 4 estados terminais;
  - rejeitar por perfil e estado;
  - cancelamento por manutenção;
  - filtros de tipo e local da busca;
  - reserva cancelada libera a sala;
  - intervalo inválido na busca de professor e material;
  - retirada por reserva e pendência de devolução;
  - bordas de senha, e-mail e local no cadastro de usuário;
  - colisão de manutenção só com reserva ativa da mesma sala;
  - hash de senha malformado nunca confere;
  - filtros da auditoria.
- **Código morto:** a sobrecarga `@Deprecated` `AuditoriaService.registrar(..., autorId)` não tinha mais chamada e saiu.
  `CadastroRecursoService.listarSalasAtivas`, usado só por testes, passou a ser usado pelo catálogo (`GET /recursos`), que
  repetia o mesmo filtro.
- **Código defensivo nunca exercido:** a checagem de autor nulo na devolução de material saiu (a rota sempre passa o
  usuário do token).

## Limites

- O PIT mutou só o domínio. A camada HTTP (`api`) é coberta pelos testes de API e pela matriz de autorização, mas não
  foi mutada.
- Os ITs (PostgreSQL e navegador) ficaram fora para a rodada caber em minutos; mutantes que só eles matariam aparecem
  como sobreviventes.
- A classificação "equivalente" dos sobreviventes é análise do autor, não prova formal.
