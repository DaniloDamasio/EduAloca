# Mutação manual das regras críticas e auditoria de rastreabilidade

**Data:** 2026-11-11 · **Commit-base:** `ee540c5` (branch `matriz-autorizacao`) · **Executor:** auditoria de
rastreabilidade sobre [RTM.md](../RTM.md).

## 1. Método

Para cada regra crítica (sobreposição, dupla reserva, aprovação de restrito, manutenção, autorização e auditoria),
uma regra por vez foi quebrada **no código de produção** (`src/main`). Em seguida rodaram os testes que a RTM, ou o
próprio nome do teste, liga à regra. Depois a mudança foi desfeita com `git restore`, e só então se passou à próxima
quebra. O script de execução conferia, antes e depois de cada mutação, que `git status --porcelain src/main` estava
vazio.

- **Mutante morto:** ao menos um teste ligado falhou (resultado **VERMELHO**).
- **Lacuna:** todos os testes ligados ficaram verdes com a regra quebrada (resultado **VERDE**). Cada lacuna foi
  rodada de novo contra a suíte unitária inteira (e contra os ITs, no caso de D3). Se continuou verde ali também, o
  teste que faltava foi escrito e a mesma mutação foi repetida contra ele.
- Comando por mutação: `./mvnw -B -o -Djacoco.skip=true -Dtest=<testes ligados> test` (com `verify -Dit.test=<ITs>`
  quando havia IT ligado). O gate JaCoCo foi desligado só nas rodadas de mutação, porque elas rodam um subconjunto
  da suíte.
- Quando um teste unitário ligado falhou, o Maven para antes do Failsafe. Nesses casos (S1–S4, S6, U1, U3–U6, U9) a
  coluna "executados" conta só os unitários.
- Ambiente: macOS, JDK Temurin 21.0.9, Maven 3.9.9, Docker via Colima 0.10.3. Testcontainers precisou de
  `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` e `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
  Sem essas variáveis, os 4 ITs falham localmente com "Could not find a valid Docker environment" (no CI eles rodam).

**Linha de base no commit-base, antes de qualquer mutação:** suíte unitária verde e 14/14 ITs verdes localmente. No
CI, o run [43122784966](https://github.com/DaniloDamasio/EduAloca/actions/runs/43122784966) (`pull_request`, commit
`ee540c5`) teve todos os passos verdes (build e testes, SonarCloud, artefatos). O artefato `test-reports` desse run
contém as 31 classes de teste com 0 falhas e 0 erros. Os 3 casos pulados são as linhas `@bloqueado` de BDD-R4-01.

## 2. Resultados

"Executados" é o número de casos de teste que rodaram na mutação. "Falharam" lista os testes que detectaram a quebra.

### 2.1 Sobreposição (RF-15, RF-16)

| ID | Quebra feita | Arquivo | Testes executados (ligados) | Executados | Resultado | Quem detectou |
|---|---|---|---|---|---|---|
| S1 | `sobrepoe` vira intervalo fechado: encostar passa a sobrepor | `domain/comum/Intervalos.java` | `RegrasCriticasTest`, `RejeicaoSobreposicaoAuditoriaTest`, `ReservaRepositorioJdbcIT` | 33 | VERMELHO (4) | `RegrasCriticasTest.rc02T2_reservasContiguas…`, `rc04T3_reservaNoLimiteDaManutencao…`; `RejeicaoSobreposicaoAuditoriaTest.alteracaoRecusadaPorSobreposicao_gravaRejeicao`, `semSobreposicao_naoGravaRejeicao` |
| S2 | `existeConflito` do repositório em memória sempre `false` | `domain/reserva/ReservaRepositorioEmMemoria.java` | idem | 33 | VERMELHO (9) | `RegrasCriticasTest.rc02T3/T4/T5/T6/T8…`; `RejeicaoSobreposicaoAuditoriaTest.criacaoRecusadaPorSobreposicao…`, `alteracaoRecusadaPorSobreposicao…` |
| S3 | Repositório em memória ignora conflito na agenda do professor | `domain/reserva/ReservaRepositorioEmMemoria.java` | idem | 33 | VERMELHO (2) | `RegrasCriticasTest.rc02T6_sobreposicaoAgendaProfessor…`, `rc02T8_apenasUmRecursoEmConflito…` |
| S4 | `alterar()` não checa sobreposição no serviço | `domain/reserva/ReservaService.java` | idem | 33 | VERMELHO (1) | `RejeicaoSobreposicaoAuditoriaTest.alteracaoRecusadaPorSobreposicao_gravaRejeicao` |
| S5 | Constraint do banco sem `WHERE (ativa)`: cancelada ocupa o recurso | `resources/db/postgres/schema.sql` | `RegrasCriticasTest`, `ReservaRepositorioJdbcIT` | 34 | VERMELHO (1) | `ReservaRepositorioJdbcIT.canceladaLiberaHorario` |
| S6 | `CANCELADA` passa a contar como ativa | `domain/reserva/EstadoReserva.java` | `RegrasCriticasTest`, `RejeicaoSobreposicaoAuditoriaTest`, `ReservaRepositorioJdbcIT` | 33 | VERMELHO (1) | `RegrasCriticasTest.rc02T9_reservaCanceladaNaoBloqueiaNovaReserva` |

**Achado de rastreabilidade:** para RF-15/RF-16, a RTM cita só `ReservaRepositorioJdbcIT`, mas a aplicação usa o
repositório em memória por padrão (o PostgreSQL só entra com `edualoca.persistencia.url`). S2, S3, S4 e S6 só são
detectados por `RegrasCriticasTest` e `RejeicaoSobreposicaoAuditoriaTest`, que a RTM não liga a RF-15/RF-16.

### 2.2 Dupla reserva (RF-17, RNF-08)

| ID | Quebra feita | Arquivo | Testes executados (ligados) | Executados | Resultado | Quem detectou |
|---|---|---|---|---|---|---|
| D1 | Remove a constraint `ex_reserva_recurso_sem_sobreposicao` | `resources/db/postgres/schema.sql` | `RegrasCriticasTest`, `ConcorrenciaReservaIT`, `ConcorrenciaReservaMultiInstanciaIT`, `ReservaRepositorioJdbcIT` | 38 | VERMELHO (4) | `ConcorrenciaReservaMultiInstanciaIT.agendaDoProfessorDisputadaEntreDuasInstancias`; `ReservaRepositorioJdbcIT.sobreposicaoRecusadaPeloBanco`, `professorProtegido`, `canceladaLiberaHorario` |
| D2 | Violação da constraint não vira 409 (sobe como 500) | `persistencia/ReservaRepositorioJdbc.java` | idem | 38 | VERMELHO (4) | `ConcorrenciaReservaMultiInstanciaIT.salaDisputadaEntreDuasInstancias`; `ReservaRepositorioJdbcIT.sobreposicaoRecusadaPeloBanco`, `professorProtegido`, `canceladaLiberaHorario` |
| D3 | Serviço deixa de travar o recurso (sem `ReentrantLock`) | `domain/reserva/ReservaService.java` | `RegrasCriticasTest`, `ConcorrenciaReservaIT`, `ConcorrenciaReservaMultiInstanciaIT` | 32 | **VERDE: lacuna** | nenhum. Suíte inteira: 388 casos (unitários e ITs), 0 falhas |

**D3:** sem o lock, o modo em memória (padrão da aplicação) fica com a corrida "checar conflito → gravar" aberta.
`RC-03-T2/T3` ficam verdes porque a janela é curta demais para o escalonador abri-la, e os ITs ficam verdes porque
lá a constraint do banco cobre a falta do lock. **Teste escrito:**
`src/test/java/com/edualoca/ConcorrenciaReservaEmMemoriaTest.java` :: `criacoesSimultaneasNaChecagem_soUmaEAceita`.
Um repositório decorado segura as duas threads numa `CyclicBarrier` dentro de `existeConflito`, o que torna o
resultado determinístico. Com o código original, o teste passa (1 aceita, 1×409, 1 linha gravada). Com D3 aplicada,
falha em 3 de 3 execuções com `reservas aceitas ==> expected: <1> but was: <2>`.

### 2.3 Aprovação de recurso restrito (RF-12, RF-18, RF-19, RF-20)

| ID | Quebra feita | Arquivo | Testes executados (ligados) | Executados | Resultado | Quem detectou |
|---|---|---|---|---|---|---|
| A1 | Reserva com recurso restrito nasce `APROVADA` | `domain/reserva/ReservaService.java` | `AprovacaoBloqueioFeatureTest`, `AprovacaoRecursosRestritosTest`, `MatrizAutorizacaoApiTest` | 142 | VERMELHO (132) | feature *Reserva com recurso restrito nasce SOLICITADA*; `MatrizAutorizacaoApiTest` (57 células, 35 de erro, 19 de entrada inválida, posse); outros |
| A2 | Material restrito não conta como restrito | `domain/reserva/ReservaService.java` | idem | 142 | **VERDE: lacuna** | nenhum. Suíte unitária inteira: 374 casos, 0 falhas |
| A3 | `aprovar()` sem a checagem de perfil Responsável/Administrador | `domain/reserva/ReservaService.java` | idem | 142 | VERMELHO (1) | só `AprovacaoRecursosRestritosTest.rf19T3_solicitanteTentaAprovar_deveRetornar403` |
| A4 | Qualquer Responsável decide, sem checar o local (DEF-010) | `domain/reserva/ReservaService.java` | idem | 142 | VERMELHO (4) | `MatrizAutorizacaoApiTest.celulaDaMatriz` ×2, `celulaDeErroEmProblemDetail` ×2 |
| A5 | `aprovar()` aceita reserva fora de `SOLICITADA` | `domain/reserva/ReservaService.java` | idem + `MaquinaEstadosReservaTest` | 155 | VERMELHO (1) | só `AprovacaoRecursosRestritosTest.rf19T4_aprovarReservaForaDeSolicitada_deveSerRejeitado` |
| A6 | Rejeição aceita justificativa em branco | `domain/reserva/ReservaService.java` | idem | 142 | VERMELHO (3) | feature *Rejeição sem justificativa é recusada* (exemplos 2 e 3); `AprovacaoRecursosRestritosTest.rf20T2…` |

**A2:** nenhum teste reservava material restrito. **Teste escrito:**
`src/test/java/com/edualoca/AprovacaoRecursosRestritosTest.java` :: `rf12T2_materialRestrito_reservaNasceSolicitada`
(sala comum mais projetor restrito → `SOLICITADA`). Passa com o código original e falha com A2 em 3 de 3 execuções.

**A3 e A5:** cada um é detectado por um único teste, e a RTM não cita nenhum dos dois. A feature e a matriz ficam
verdes no A3 porque todos os cenários usam sala, e com sala a checagem de local (`exigirDecisorCompetente`) já
devolve 403 ao Solicitante. A checagem de perfil só faz diferença em reserva sem sala.

**RF-18 sem evidência real:** `AprovacaoRecursosRestritosTest.rf18T1_transicaoDiretaSolicitadaParaEmUso_deveSerBloqueada`
chama `aprovar` com um UUID que nunca existiu e recebe 409 por "reserva inexistente". O teste não exercita nenhuma
transição para `EM_USO`, e o código de produção também não tem essa transição (pendência P-05 da RTM). Não houve
mutação possível: não existe regra no código para quebrar.

### 2.4 Manutenção (RF-11, RF-21, RF-22, RF-23)

| ID | Quebra feita | Arquivo | Testes executados (ligados) | Executados | Resultado | Quem detectou |
|---|---|---|---|---|---|---|
| M1 | Criação de reserva ignora manutenção | `domain/reserva/ReservaService.java` | `AprovacaoBloqueioFeatureTest`, `BddAprovacaoBloqueioTest`, `RegrasCriticasTest` | 57 | VERMELHO (6) | feature *Reserva totalmente dentro da manutenção é recusada*; `BddAprovacaoBloqueioTest.reservaDeRecursoDeQualquerTipoEmManutencao…` ×3; `RegrasCriticasTest.rc02T10`, `rc04T2` |
| M2 | Busca de disponibilidade mostra sala em manutenção | `domain/recurso/PesquisaRecursoService.java` | `AprovacaoBloqueioFeatureTest`, `PesquisaDisponibilidadeTest` | 33 | VERMELHO (2) | feature *Recurso em manutenção não aparece na busca…*; `PesquisaDisponibilidadeTest.rf11T1…` |
| M3 | Manutenção sobre reserva `APROVADA`/`EM_USO` cadastrada sem confirmação | `domain/recurso/CadastroRecursoService.java` | `AprovacaoBloqueioFeatureTest`, `BddAprovacaoBloqueioTest`, `RegrasCriticasTest` | 57 | VERMELHO (3) | feature *Cadastrar manutenção sobre reserva APROVADA…*; `RegrasCriticasTest.rc04T4`, `rc04T5` |
| M4 | Colisão de manutenção ignora reserva `EM_USO` | `domain/recurso/CadastroRecursoService.java` | idem | 57 | VERMELHO (1) | só `RegrasCriticasTest.rc04T5_manutencaoSobrepoeReservaEmUso…` |
| M5 | Bloqueio por manutenção só vale para sala | `domain/reserva/ReservaService.java` | idem | 57 | VERMELHO (2) | `BddAprovacaoBloqueioTest.reservaDeRecursoDeQualquerTipoEmManutencao…` (material, professor) |
| M6 | Borda da manutenção passa a bloquear | `domain/reserva/ReservaService.java` | idem | 57 | VERMELHO (1) | só `RegrasCriticasTest.rc04T3_reservaNoLimiteDaManutencao…` |

**Defeito encontrado na leitura do código (não é mutação):** em `PesquisaRecursoService`, `disponivel()` só olha
manutenção quando o recurso é `Sala`, e `buscarMateriais` não olha manutenção nenhuma. Professor ou material em
manutenção continuam aparecendo em `buscarProfessores`/`buscarMateriais`, contrariando o RF-11. Hoje a API só expõe
`/salas/disponiveis`, por isso nenhum teste de API pega o problema. Fica registrado como candidato a DEF-012, sem
correção nesta rodada.

### 2.5 Autorização (RF-01, RF-02, RF-03)

| ID | Quebra feita | Arquivo | Testes executados (ligados) | Executados | Resultado | Quem detectou |
|---|---|---|---|---|---|---|
| Z1 | Posse ignorada: qualquer Solicitante altera/cancela reserva existente | `domain/reserva/ReservaService.java` | `MatrizAutorizacaoApiTest`, `AprovacaoBloqueioFeatureTest` | 135 | VERMELHO (16) | `MatrizAutorizacaoApiTest` (células, `Posse.alterarReservaAlheia`, `Posse.cancelarReservaAlheia`); feature *Solicitante não cancela reserva de terceiro* |
| Z2 | Qualquer perfil cadastra manutenção | `api/recurso/ManutencaoController.java` | `MatrizAutorizacaoApiTest`, `ApiSegurancaTest` | 129 | VERMELHO (5) | `ApiSegurancaTest.solicitanteNaoCadastraManutencao`; `MatrizAutorizacaoApiTest` ×4 |
| Z3 | Responsável/Administrador criam reserva (DEF-009) | `domain/reserva/ReservaService.java` | `MatrizAutorizacaoApiTest` | 114 | VERMELHO (4) | `MatrizAutorizacaoApiTest` ×4 |
| Z4 | Solicitante consulta histórico | `domain/reserva/ReservaService.java` | `HistoricoReservaApiTest`, `MatrizAutorizacaoApiTest` | 122 | VERMELHO (3) | `HistoricoReservaApiTest.solicitanteRecebe403`; `MatrizAutorizacaoApiTest` ×2 |
| Z5 | Solicitante lê relatório de utilização | `domain/relatorio/RelatorioService.java` | `RelatorioUtilizacaoApiTest`, `MatrizAutorizacaoApiTest` | 130 | VERMELHO (4) | `RelatorioUtilizacaoApiTest.solicitante403`, `solicitanteComParametroInvalido403`; `MatrizAutorizacaoApiTest` ×2 |
| Z6 | Solicitante consulta reserva alheia pelo id | `domain/reserva/ReservaService.java` | `MatrizAutorizacaoApiTest` | 114 | VERMELHO (4) | `MatrizAutorizacaoApiTest` (células e `Posse.idAlheioEIdInexistenteSaoIndistinguiveis`) |
| Z7 | Token desconhecido aceito como o primeiro usuário logado | `api/seguranca/TokenService.java` | `MatrizAutorizacaoApiTest`, `ApiSegurancaTest`, `HistoricoReservaApiTest`, `RelatorioUtilizacaoApiTest` | 153 | VERMELHO (3) | `ApiSegurancaTest.semTokenValido_recusaCom401` ×2; `RelatorioUtilizacaoApiTest.tokenDesconhecido401` |

### 2.6 Auditoria (RF-27, RF-28, RF-29)

| ID | Quebra feita | Arquivo | Testes executados (ligados) | Executados | Resultado | Quem detectou |
|---|---|---|---|---|---|---|
| U1 | Transição de estado não grava histórico | `domain/reserva/ReservaService.java` | `HistoricoAuditavelReservaTest`, `AprovacaoBloqueioFeatureTest`, `BddAprovacaoBloqueioTest`, `HistoricoReservaTransacaoIT` | 58 | VERMELHO (8) | `HistoricoAuditavelReservaTest` (aprovação, rejeição, cancelamento, falha ao gravar…) |
| U2 | Histórico gravado fora da transação da mudança de estado | `domain/reserva/ReservaService.java` | `HistoricoAuditavelReservaTest`, `HistoricoReservaTransacaoIT` | 33 | VERMELHO (1) | só `HistoricoReservaTransacaoIT.rollbackNaTransicaoNaoDeixaAuditoriaOrfa` (PostgreSQL real) |
| U3 | Aprovação não gera evento `APROVACAO` | `domain/reserva/ReservaService.java` | `AprovacaoBloqueioFeatureTest`, `BddAprovacaoBloqueioTest`, `HistoricoAuditavelReservaTest` | 58 | VERMELHO (3) | feature *Responsável aprova…* e BDD-R4-01 ex. 1; `BddAprovacaoBloqueioTest.transicaoPermitida…` |
| U4 | Cancelamento não gera evento `CANCELAMENTO` | `domain/reserva/ReservaService.java` | idem | 58 | VERMELHO (4) | BDD-R4-01 ex. 3 e 4; `BddAprovacaoBloqueioTest.transicaoPermitida…` ×2 |
| U5 | Histórico grava o Solicitante como autor em vez de quem decidiu | `domain/reserva/ReservaService.java` | `HistoricoAuditavelReservaTest`, `AprovacaoBloqueioFeatureTest`, `BddAprovacaoBloqueioTest`, `HistoricoReservaTransacaoIT` | 58 | VERMELHO (2) | `HistoricoAuditavelReservaTest` (aprovação, rejeição) |
| U6 | Aprovação gera evento duplicado | `domain/reserva/ReservaService.java` | `AprovacaoBloqueioFeatureTest`, `BddAprovacaoBloqueioTest`, `HistoricoAuditavelReservaTest` | 58 | VERMELHO (3) | feature *Responsável aprova…* e BDD-R4-01 ex. 1; `BddAprovacaoBloqueioTest.transicaoPermitida…` |
| U7 | Remove o trigger que recusa UPDATE/DELETE no histórico | `resources/db/postgres/schema.sql` | `HistoricoAuditavelReservaTest`, `HistoricoReservaApiTest`, `HistoricoReservaTransacaoIT` | 41 | VERMELHO (1) | só `HistoricoReservaTransacaoIT.bancoRecusaAlterarOuApagarHistorico` |
| U8 | Cadastro de manutenção não gera auditoria | `domain/recurso/CadastroRecursoService.java` | `AuditoriaTest`, `RejeicaoSobreposicaoAuditoriaTest` | 12 | **VERDE: lacuna** | nenhum. Suíte unitária inteira: 374 casos, 0 falhas |
| U9 | Criação de reserva não grava histórico | `domain/reserva/ReservaService.java` | `HistoricoAuditavelReservaTest`, `AprovacaoBloqueioFeatureTest`, `HistoricoReservaTransacaoIT` | 50 | VERMELHO (14) | `HistoricoAuditavelReservaTest` (criação, `reservaIniciadaNaoEApagada` ×6…) |

**U8:** os testes RF-28 de `AuditoriaTest` (`rf27T1`, `rf28T1`, `rf28T2`) consultam um UUID aleatório por
`AuditoriaService.consultarPorEntidade`. Esse método **inventa um evento** quando a entidade não tem nenhum
(`AuditoriaService.java:40-44`: devolve `REJEICAO_SOBREPOSICAO` para "Reserva" e `CRIACAO` para o resto, com autor
aleatório). Por isso esses três testes passam com qualquer código e não servem de evidência. **Teste escrito:**
`src/test/java/com/edualoca/AuditoriaCadastroTest.java` :: `escritaDeCadastroGeraAuditoria` (7 casos: criação de
sala, professor e material, manutenção de sala, material e professor, inativação de sala). O teste lê
`Banco.auditoria` direto. Passa com o código original e, com U8 aplicada, falha nos 3 casos de manutenção em 3 de 3
execuções (`expected: <1> but was: <0>`).

**Defeito encontrado e corrigido:**
[DEF-011](../defeitos/DEF-011-auditoria-inventa-evento-inexistente.md). Inventar um evento de auditoria em produção
fere o RF-29, porque a consulta afirma ações que nunca aconteceram. Correção feita em seguida: red `1826216` (teste
`def011_entidadeSemEventos_devolveListaVazia`, mais `rf27T1`, `rf28T1` e `rf28T2` reescritos com ações reais) e
green `fc5fe22`. Reteste por mutação: remover a auditoria de aprovação, de criação de sala e de rejeição por
sobreposição derruba, respectivamente, `rf27T1`, `rf28T1` e `rf28T2`. Antes da correção, os três passavam com
qualquer código. Observação: os eventos de cadastro gravam
`autorId = null`, porque `CadastroRecursoService` não recebe o usuário. O RF-28 pede só a entrada de auditoria, mas
sem autor a investigação "quem fez" não é possível.

## 3. Resumo

| Regra | Mutantes | Mortos pelos testes ligados | Lacunas | Teste escrito |
|---|---|---|---|---|
| Sobreposição | 6 | 6 | 0 | — |
| Dupla reserva | 3 | 2 | 1 (D3) | `ConcorrenciaReservaEmMemoriaTest` |
| Aprovação de restrito | 6 | 5 | 1 (A2) | `AprovacaoRecursosRestritosTest.rf12T2_…` |
| Manutenção | 6 | 6 | 0 | — |
| Autorização | 7 | 7 | 0 | — |
| Auditoria | 9 | 8 | 1 (U8) | `AuditoriaCadastroTest` |
| **Total** | **33** | **30 (90,9%)** | **3** | **3 de 3 lacunas fechadas e confirmadas por nova mutação** |

Depois dos testes novos, os 33 mutantes são mortos (D3, A2 e U8 repetidos 3 vezes cada, sempre vermelhos), e
`./mvnw -B verify` local fica verde: 386 unitários (3 pulados, os `@bloqueado`), 14 ITs, gate JaCoCo atendido.
**Os três testes novos ainda não têm execução no CI:** só existirão no CI depois de commit e push.

## 4. Auditoria da RTM: requisitos críticos com evidência

Critério para contar um requisito como **com evidência**: há linha na RTM com risco, o teste é localizável por
caminho e nome (conferido no código), e o teste aparece verde no relatório do CI do commit `ee540c5` (run
43122784966). Execução local não basta para esse critério.

| Requisito crítico | Risco na RTM | Teste localizável | Verde no CI `ee540c5` | Situação |
|---|---|---|---|---|
| RF-01 | sim | sim | sim | Com evidência |
| RF-02 | sim | sim | sim | Com evidência |
| RF-03 | sim | sim | sim | Com evidência |
| RF-04 / RF-05 / RF-06 (CRUD sala, professor, material) | não | não na RTM (`CadastroConsultaTest` existe, não auditado) | — | **Sem evidência** (3 requisitos) |
| RF-11 | sim | sim | sim | Com evidência (só salas; ver defeito em 2.4) |
| RF-12 | sim | sim | sim | Com evidência para sala. Material restrito só pelo teste novo, **sem CI** |
| RF-14 | sim | sim | sim | Com evidência |
| RF-15 | sim | sim | sim | Com evidência (RTM deve citar também `RegrasCriticasTest`, ver 2.1) |
| RF-16 | sim | sim | sim | Com evidência (idem) |
| RF-17 | sim | sim | sim | Com evidência para PostgreSQL. Modo em memória só pelo teste novo, **sem CI** |
| RF-18 | não | não (`rf18T1` não exercita a regra) | — | **Sem evidência** |
| RF-19 | sim | sim | sim | Com evidência (RTM deve citar `rf19T3`, `rf19T4`) |
| RF-21 | sim | sim | sim | Com evidência |
| RF-22 | sim | sim | sim | Com evidência |
| RF-24 / RF-25 (retirada e devolução) | não | não na RTM (`RetiradaDevolucaoMaterialTest` existe, não auditado) | — | **Sem evidência** (2 requisitos) |
| RF-27 | sim | sim | sim | Com evidência |
| RF-28 | parcial (a linha cobre só `REJEICAO_SOBREPOSICAO`) | `AuditoriaCadastroTest` (novo) | **não** | **Sem evidência no CI**: executado só localmente |
| RF-29 | sim | sim | sim | Com evidência |
| RF-30 | sim | sim | sim | Com evidência |
| RF-38 | sim | sim | sim | Com evidência |
| RNF-04 | sim | sim | sim | Com evidência |
| RNF-05 (hash de senha) | não | não | — | **Sem evidência** |
| RNF-06 | sim | sim | sim | Com evidência |
| RNF-07 (SonarCloud sem Critical/Blocker) | não | — | passo do Sonar verde, mas sem relatório de issues anexado | **Sem evidência** |
| RNF-08 | sim | sim | sim | Com evidência |
| RNF-10 (JaCoCo ≥80/70) | não | — | gate atendido no CI, mas sem linha na RTM | **Sem evidência na RTM** |
| RNF-11 (RTM completa) | — | — | — | **Não atendido** (este próprio levantamento) |
| RNF-12 (CI bloqueia merge) | não | — | run verde e `main` exige o check "Build e Testes" (proteção de branch conferida) | **Sem evidência na RTM** |
| RNF-15 (MVC em camadas) | não | — | — | **Sem evidência** |

**Resultado: 19 de 32 requisitos críticos com evidência = 59,4%.** Dois deles (RF-12 e RF-17) tinham uma parte da
regra sem teste, já fechada pelos testes novos, mas essa parte ainda não tem execução no CI. Contando só os
requisitos sem nenhuma parte descoberta no commit `ee540c5`, a taxa estrita é **17 de 32 = 53,1%**.

## 5. Pendências

1. Commitar os três testes novos e obter um run de CI verde. Até lá, RF-28 e as partes novas de RF-12 e RF-17 não
   contam como evidência.
2. ~~Corrigir `AuditoriaService.consultarPorEntidade` e reescrever `AuditoriaTest.rf27T1`, `rf28T1` e `rf28T2`.~~
   Feito em [DEF-011](../defeitos/DEF-011-auditoria-inventa-evento-inexistente.md) (`1826216` red, `fc5fe22` green).
   CI pendente.
3. Excluir da busca o professor e o material em manutenção (`PesquisaRecursoService`, candidato a DEF-012).
4. RF-18: decidir P-05 (iniciar uso) e, até lá, remover ou renomear `rf18T1`, que não testa o que o nome diz.
5. Abrir linhas na RTM, com risco e auditoria por mutação, para RF-04/05/06, RF-24/25, RNF-05, RNF-07, RNF-10,
   RNF-12 e RNF-15.
