# Status dos requisitos — auditoria de qualidade

> Data: 10/11/2026 · Branch auditada: `concorrencia-reservas-testcontainers` (commit `43b0302`, igual à `main` após o PR #8).
> Fonte normativa: [enunciado.md](enunciado.md) (14 requisitos funcionais e regras críticas), decomposta em
> [REQUISITOS.md](REQUISITOS/REQUISITOS.md) (RF-01 a RF-39). Rastreabilidade atual: [RTM.md](RTM.md).
>
> **Atualização 11/11/2026** (branch `relatorio-utilizacao`): entrega de `GET /relatorios/utilizacao`. Revistos o item #12,
> a lista de rotas, a execução da suíte e as pendências P6 e P8. As demais linhas continuam como na auditoria de 10/11.

## 1. Método e critério

- Para cada requisito, li o código de produção, os testes e a RTM e conferi se a regra é exercida **pela aplicação em execução**
  (rotas HTTP) ou só por chamada direta ao domínio.
- **Feito** = há teste automatizado que **falharia se a regra quebrasse** e a regra está ligada ao fluxo real.
  **Parcial** = parte da regra existe/testada, mas falta fluxo, rota, teste significativo ou há defeito.
  **Ausente** = não há implementação.
- Teste que passa com qualquer implementação (UUID aleatório que não existe, `assertTrue(x >= 0)`, auditoria fabricada)
  **não conta** como evidência. A lista desses testes está na seção 4.

### Execução de `./mvnw -B verify`

| Execução | Resultado |
|---|---|
| Local, sem `DOCKER_HOST` (Docker via Colima) | **BUILD FAILURE**: 204 testes unitários verdes (3 *skipped* `@bloqueado`), mas os 3 ITs Testcontainers (`ConcorrenciaReservaIT`, `ConcorrenciaReservaMultiInstanciaIT`, `ReservaRepositorioJdbcIT`) falham com *Could not find a valid Docker environment*. |
| Local, com `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` e `TESTCONTAINERS_RYUK_DISABLED=true` | **BUILD SUCCESS**: 204 unitários + 10 ITs; gate JaCoCo atendido (linhas **95,9 %**, branches **83,2 %**). |
| CI (GitHub Actions, último run em `main`, 10/11) | Verde, etapa SonarCloud executada. SonarCloud: 0 bugs, 0 vulnerabilidades, 0 hotspots, 4 code smells, cobertura 92,1 %, quality gate OK. |
| Local, branch `relatorio-utilizacao` (11/11), com `DOCKER_HOST` do Colima | **BUILD SUCCESS**: 262 unitários (3 *skipped* `@bloqueado`) + 14 ITs; gate JaCoCo atendido (linhas **96,5 %**, branches **85,9 %**). |
| Smoke test do jar (`java -jar target/edualoca-0.0.1-SNAPSHOT.jar`) | Sobe em < 1 s (modo memória). `POST /auth/login` e `GET /salas/disponiveis` respondem. Rotas `/salas`, `/professores`, `/materiais`, `/relatorios`, `/auditoria`, `/v3/api-docs`, `/swagger-ui.html`, `/index.html` → 404; `PUT /reservas/{id}` → 405. |

Rotas HTTP existentes (todas): `POST /auth/login`, `GET /salas/disponiveis`, `POST /manutencoes`, `POST /reservas`,
`GET /reservas/{id}`, `POST /reservas/{id}/aprovacao`, `/rejeicao`, `/cancelamento`, `DELETE /reservas/{id}`.
Acrescentadas depois da auditoria: `GET /reservas/{id}/historico` e `GET /relatorios/utilizacao` (11/11).

## 2. Requisitos funcionais obrigatórios (enunciado #1 a #14)

| # | Requisito | Situação | Evidência (arquivo :: teste) | O que falta / defeito | Risco se ficar de fora |
|---|---|---|---|---|---|
| 1 | Autenticação e autorização por perfil (RF-01–03) | **Parcial** | `LoginApiTest` :: `credencialValida_retornaTokenUtilizavel`, `senhaIncorreta_401`, `emailInexistente_401`, `camposAusentes_400`; `ApiSegurancaTest` :: `semTokenValido_recusaCom401`, `solicitanteNaoCadastraManutencao`; feature *Solicitante tenta aprovar a própria reserva* (BDD-R2-01..03); `AutenticacaoAutorizacaoTest` :: `rf03T2_…`, `rf03T3_…` | `AutenticacaoService.autenticar` **sempre devolve `Perfil.SOLICITANTE`**: nenhum usuário real vira Responsável ou Administrador; todos os testes desses perfis usam token forjado com `new Usuario(..., Perfil.X)`. Senhas em texto plano em `Banco.credenciais`; `SenhaHasher` é SHA-256 sem sal. Tokens sem expiração/revogação. `rf02T1..T3` testam `AutenticacaoService.autorizar`, que **não é chamado por nenhum código de produção**. | Limitador de nota (falha de autorização): aprovação, manutenção e relatórios não podem ser demonstrados; RNF-05 violado (senha em texto plano). |
| 2 | Cadastro e consulta de salas, professores e materiais (RF-04–07) | **Parcial** | `CadastroConsultaTest` :: `rf04T1..T4`, `rf05T2_identificadorInstitucionalDuplicado…`, `rf06T2_…`, `rf07T2_salaInativada…` (domínio) | Sem rota HTTP de cadastro, edição ou listagem; só existe criar + inativar sala (sem edição, sem inativar professor/material, sem CRUD de usuários). `CadastroRecursoService` não verifica perfil e grava auditoria com autor `null`. | Administrador não consegue operar o sistema; requisito "Crítico" (RF-04–06) sem entrega na aplicação. |
| 3 | Pesquisa por tipo, capacidade, localização, competência e disponibilidade (RF-08–11) | **Parcial** | `PesquisaDisponibilidadeTest` :: `rf08T1..T3`, `rf09T1`, `rf10T1`, `rf10_materialTotalmenteReservado…`, `rf11_salaComReservaAtivaSobreposta…`, `rf11_salaComReservaEmUsoSobreposta…`; feature *Recurso em manutenção não aparece na busca de disponibilidade* | Só salas têm rota HTTP; professores (competência) e materiais apenas no domínio. Busca de material e professor **não exclui manutenção** (só `Sala` é checada em `PesquisaRecursoService.disponivel`). Busca lê `Banco.reservas`: com PostgreSQL ativo ela **ignora as reservas gravadas no banco** e mostra sala ocupada como livre. | Busca oferece recurso indisponível (RF-11 é Crítico); critério "competência" do enunciado não acessível ao usuário. |
| 4 | Criação, alteração e cancelamento de reservas (RF-12–14) | **Parcial** | Criação: `ReservaCicloVidaTest` :: `rf12T1..T5`; feature *Reserva com recurso restrito nasce SOLICITADA*. Alteração: `ReservaCicloVidaTest` :: `rf13T1..T4`, `ReservaAlteracaoFalhaGravacaoTest` :: `gravacaoRecusada_restauraHorarioAnteriorEPropagaErro`. Cancelamento: `rf14T1..T5`; features *Cancelamento sem motivo é recusado*, *Solicitante não cancela reserva de terceiro* | **Alteração não tem rota HTTP** (`PUT /reservas/{id}` → 405). Não há caminho para alterar recursos da reserva. | Um dos três verbos do requisito não existe na aplicação. |
| 5 | Sobreposição de horários, inclusive agenda do professor (RF-15–16) | **Feito** | `RegrasCriticasTest` :: `rc02T3_sobreposicaoParcial…`, `rc02T4_reservaEnglobaOutra…`, `rc02T5_intervalosIdenticos…`, `rc02T6_sobreposicaoAgendaProfessorRecursoAcessorio…`, `rc02T8_apenasUmRecursoEmConflito…`, `rc02T2_reservasContiguas…`, `rc02T9_reservaCanceladaNaoBloqueia…`; `ReservaRepositorioJdbcIT` :: `sobreposicaoRecusadaPeloBanco`, `professorProtegido`, `intervalosEncostadosPermitidos` | Ressalvas: não há teste de **material contra material**; mensagem 409 não informa recurso nem horário conflitante (critério de RF-15). | Baixo; ressalva sobre material na seção 3. |
| 6 | Proteção contra dupla reserva em solicitações simultâneas (RF-17, RNF-08) | **Feito** | `ConcorrenciaReservaIT` :: `salaDisputadaPorVinteSolicitacoesSimultaneas`, `agendaDoProfessorDisputadaPorVinteSolicitacoesSimultaneas`; `ConcorrenciaReservaMultiInstanciaIT` :: `salaDisputadaEntreDuasInstancias`, `agendaDoProfessorDisputadaEntreDuasInstancias`; `RegrasCriticasTest` :: `rc03T2`, `rc03T3`; constraint `ex_reserva_recurso_sem_sobreposicao`; [DEF-001](defeitos/DEF-001-dupla-reserva-entre-instancias.md); JMeter [resultado-01](performance/resultado-01.md) | Os ITs só rodam com Docker acessível (falharam localmente sem `DOCKER_HOST`). `rc03T4_falhaParcialDuranteCriacao…` não simula falha nenhuma (seção 4). | Limitador de nota se o avaliador rodar o build sem Docker configurado e ver vermelho. |
| 7 | Aprovação obrigatória para recursos restritos (RF-18–20) | **Parcial** | `ReservaCicloVidaTest` :: `rf12T2`, `rf12T3`; `AprovacaoRecursosRestritosTest` :: `rf19T1`, `rf19T2`, `rf19T3`, `rf19T4`, `rf20T1`, `rf20T2`; features *Responsável aprova reserva restrita*, *Rejeição sem justificativa é recusada*; `BddAprovacaoBloqueioTest` :: `rejeicaoComJustificativa_tornaJustificativaVisivelAoSolicitante` | A regra está aplicada e testada, mas o fluxo positivo **não é executável fora dos testes**: nenhum login gera Responsável/Administrador e não há rota para cadastrar recurso restrito (a semente só tem recursos não restritos). RF-18 ("não pula para EM_USO") é vazio porque EM_USO não existe (`rf18T1` usa UUID inexistente). | Reserva restrita fica `SOLICITADA` para sempre na aplicação real; demonstração falha. |
| 8 | Bloqueio de recursos em manutenção (RF-21–23) | **Parcial** | `RegrasCriticasTest` :: `rc04T1..T8`, `rc02T10`; `BddAprovacaoBloqueioTest` :: `reservaDeRecursoDeQualquerTipoEmManutencao_eRecusadaCom409`; features *Reserva totalmente dentro da manutenção é recusada*, *Cadastrar manutenção sobre reserva APROVADA exige confirmação explícita* | `POST /manutencoes` exige Administrador, que não consegue fazer login. Manutenções ficam só em memória (perdidas ao reiniciar, mesmo com PostgreSQL). Verificação de colisão com reserva aprovada (RF-23) lê `Banco.reservas`: com PostgreSQL ela **não enxerga as reservas**. Não há "confirmação explícita" (só recusa). Busca não exclui material/professor em manutenção. | Recurso em manutenção reservável ou oferecido na busca no modo PostgreSQL. |
| 9 | Retirada e devolução de materiais (RF-24–26) | **Parcial** | `RetiradaDevolucaoMaterialTest` :: `rf24T2`, `rf24T3`, `rf25T1`, `rf25T3`, `rf25T4`, `rf26T1..T5` (domínio) | Sem rota HTTP e sem bean Spring. Exige reserva `EM_USO`, estado que **nenhum código de produção alcança** (o teste usa `setEstado`). `RegistroRetirada` não guarda `itemId` nem condição de retirada; `rf24T1` só confere `reservaId`. Sem verificação de perfil, sem auditoria, devolução pode ser registrada duas vezes. Job de pendência não é agendado. | Requisito inteiro inexistente na aplicação; Responsável não tem o que acompanhar. |
| 10 | Histórico auditável de mudanças (RF-27–30) | **Parcial** | `AuditoriaTest` :: `rf27_criacaoDeReservaReal_deveGerarEventoDeAuditoriaReal`, `rf27_auditoriaDeEntidadesDiferentes_naoDeveSeMisturar`; `BddAprovacaoBloqueioTest` :: `transicaoPermitida_mudaEstadoEGeraUmEventoDeAuditoriaComAtorEInstante`; feature *Transição permitida muda o estado e gera auditoria* (4 linhas executáveis); feature *Reserva iniciada não pode ser apagada* | **`AuditoriaService.consultarPorEntidade` fabrica um evento** (`REJEICAO_SOBREPOSICAO` ou `CRIACAO`, autor aleatório) quando não há registro: o histórico mente e esconde auditoria ausente. Auditoria só em memória (`Banco.auditoria`, lista pública mutável), não é gravada no PostgreSQL nem na mesma transação da reserva. Cadastro e manutenção auditam autor `null`. Não há rota de consulta do histórico. | Integridade: o "histórico auditável" pode exibir eventos falsos; conta como bug crítico se apontado na apresentação. |
| 11 | Notificação simulada ou integração com API externa (RF-31–33) | **Parcial** | `IntegracaoNotificacaoExternaTest` :: `int01..int05`, `seguranca_tokenVazadoNoCorpoDeErro…` (WireMock); `NotificacoesTest` :: `rf32T2`, `rf33T1`, `rf33T2` | `NotificacaoService` e `NotificacaoHttpAdapter` **não são chamados por nenhuma transição** e não são beans Spring: na aplicação nenhuma notificação é disparada. Responsável competente é um UUID aleatório da semente. | Integração existe só como peça isolada; avaliador pode considerar "não integrado". |
| 12 | Relatório de utilização, carga horária e conflitos evitados (RF-34–36) | **Feito** (com ressalvas) | `RelatorioUtilizacaoApiTest` :: `relatorioDoMes`, `periodoVazio`, `reservaQueCruzaABorda`, `reservaCanceladaNaoConta`, `periodoDeUmAnoNoLimite`, `periodoInvalido400`, `semToken401`, `tokenDesconhecido401`, `solicitante403`, `solicitanteComParametroInvalido403` (caixa-preta pela API, valores calculados à mão); `RejeicaoSobreposicaoAuditoriaTest` :: `criacaoRecusadaPorSobreposicao_gravaRejeicao`, `recusaDaConstraintNoBanco_gravaRejeicao`, `alteracaoRecusadaPorSobreposicao_gravaRejeicao`, `recusaPorManutencao_naoGravaRejeicao` | Rota `GET /relatorios/utilizacao` com horas por recurso, carga por professor e conflitos evitados; conta `APROVADA`/`EM_USO`/`CONCLUIDA` com recorte na borda; `ReservaService` passou a gravar `REJEICAO_SOBREPOSICAO`. Ressalvas: lê `Banco.reservas` e `Banco.auditoria` (em memória), então no modo PostgreSQL perde o que houve antes de um reinício (P2); `rf34T1`/`rf35T1` (seção 4) continuam fracos e o método antigo `gerarRelatorioUtilizacao` segue sem rota; decisão "conflito contado pelo instante da tentativa" (RF-35) e "`NAO_COMPARECEU` não conta" a validar com o grupo. | Baixo; demonstração depende de login de Responsável/Administrador (#1). |
| 13 | Interface responsiva e mensagens de erro compreensíveis (RF-37–38) | **Parcial** (interface **ausente**; mensagens **feito**) | Mensagens: `ApiSegurancaTest` :: `errosDoFramework_mantemStatusSemDetalheInterno`, `entradaInvalida_recusaCom400`, `buscaSemParametro_recusaCom400`; feature *Recusa por perfil traz mensagem compreensível* | Não há nenhuma interface (sem `static/`, sem templates). Mensagens sem acentuação ("Usuario nao autorizado…"). | Metade do requisito ausente; impossibilita a demonstração para usuário final. |
| 14 | Documentação de API ou dos fluxos públicos (RF-39) | **Parcial** | README "Nota de segurança da API HTTP"; [arquitetura.md §17](arquitetura.md) (sequências críticas) | Sem OpenAPI/Swagger, sem exemplos de requisição/resposta, sem lista completa de rotas e códigos de erro. README não ensina a **executar a aplicação** (só a suíte) nem a ligar o PostgreSQL (`edualoca.persistencia.url/usuario/senha`). | Entregável "instruções de instalação" e "documentação da API" incompletos. |

**Resumo:** 3 feitos (#5, #6, #12), 11 parciais, nenhum totalmente ausente; porém #9 e a interface de #13 não existem na
aplicação em execução.

## 3. Regras críticas da especificação

| Regra | Situação | Evidência (arquivo :: teste) | Lacuna | Risco |
|---|---|---|---|---|
| Término posterior ao início | **Feito** | `RegrasCriticasTest` :: `rc01T3_fimIgualInicio…`, `rc01T4_fimAnteriorAoInicio…`, `rc01T5_inicioOuFimNulos…`, `rc01T6_alteracaoComFimMenorOuIgualInicio…`, `rc04T7_manutencaoComFimMenorOuIgualInicio…`; `PesquisaDisponibilidadeTest` :: `rf08T2`/`rf08T3`; `CHECK ck_reserva_intervalo` em `schema.sql` | — | Baixo |
| Mesmo recurso não se sobrepõe (sala, material, professor) | **Parcial** | Sala/professor: ver #5 | Sem teste de sobreposição **material × material**. Semântica de quantidade inconsistente: a busca trata material por quantidade livre, mas a constraint do banco torna o material exclusivo (busca diz "livre", criação recusa). | Médio |
| Duas solicitações simultâneas → uma aceita | **Feito** | ver #6 | Depende de Docker para rodar; manutenção cadastrada em paralelo com reserva não é serializada (check-then-act sobre `Banco.manutencoes`). | Médio (ambiente) |
| Recursos em manutenção não podem ser reservados | **Feito** (criação) | `RegrasCriticasTest` :: `rc04T2`, `rc04T3`, `rc02T10`; `BddAprovacaoBloqueioTest` :: `reservaDeRecursoDeQualquerTipoEmManutencao_eRecusadaCom409` | Busca de material/professor não exclui manutenção; manutenção não persistida. | Médio |
| Fluxo SOLICITADA → APROVADA → EM_USO → CONCLUÍDA | **Parcial** | SOLICITADA→APROVADA: `AprovacaoRecursosRestritosTest` :: `rf19T1`; feature linhas de R4-01 | **Não existem** transições para `EM_USO` e `CONCLUIDA` em produção. `MaquinaEstadosReservaTest` :: `estT2`, `estT3` passam com UUID inexistente. | Alto: bloqueia #9 e #12 |
| Estados alternativos: rejeitada, cancelada, não compareceu | **Parcial** | Rejeitada: `rf20T1`; cancelada: `rf14T1..T5` | `NAO_COMPARECEU` sem transição (`estT7` vazio). Linhas `@bloqueado` P-05/P-07/P-08 na feature. | Médio |
| Somente responsáveis aprovam recursos restritos | **Feito** (regra) | features *Solicitante tenta aprovar a própria reserva*, *…rejeitar…*, *…aprovar reserva de outro solicitante*; `AprovacaoRecursosRestritosTest` :: `rf19T3` | Na aplicação ninguém consegue ser Responsável (ver #1). Responsável aprovar a própria reserva segue em aberto (BDD-R2-05/P-01). | Alto (via #1) |
| Reservas iniciadas não podem ser apagadas | **Feito** | feature *Reserva iniciada não pode ser apagada*; `AuditoriaTest` :: `rf30T2` | `excluir` nunca apaga nada; a regra vale trivialmente. | Baixo |
| Toda mudança de estado gera auditoria | **Parcial** | `BddAprovacaoBloqueioTest` :: `transicaoPermitida_mudaEstadoEGeraUmEventoDeAuditoriaComAtorEInstante`; feature R4-01 (4 linhas) | Fallback fabricado; auditoria não persistida nem transacional; transições EM_USO/CONCLUÍDA/NÃO_COMPARECEU inexistentes. | Alto |

## 4. Testes que não protegem a regra que dizem cobrir

Passam com qualquer implementação. O enunciado avisa que "testes triviais feitos apenas para elevar o percentual não
asseguram pontuação máxima"; a cobertura de 95,9 % inclui esses testes.

| Arquivo :: teste | Por que não protege |
|---|---|
| `MaquinaEstadosReservaTest` :: `estT2`, `estT3`, `estT7`, `estT8`, `estT10`, `estT11`, `estT12`, `estT13` | Chamam `aprovar` com `UUID.randomUUID()` e esperam 409 de "reserva inexistente". Nomes "devePermitir" afirmam o contrário do que o teste checa. |
| `AprovacaoRecursosRestritosTest` :: `rf18T1` | Mesmo padrão (UUID inexistente). |
| `AuditoriaTest` :: `rf27T1`, `rf28T1`, `rf28T2` | Consultam id aleatório e só passam porque `consultarPorEntidade` fabrica o evento. |
| `AuditoriaTest` :: `rf30T1` | `excluir` lança 403 para qualquer id, inclusive inexistente. |
| `RelatoriosTest` :: `rf34T1`, `rf35T1` | `assertTrue(… >= 0)`. |
| `RequisitosNaoFuncionaisTest` :: `rnf05T1` | Compara o hash calculado na hora com a senha; a senha **está** guardada em texto plano em `Banco.credenciais`. |
| `RequisitosNaoFuncionaisTest` :: `rnf09T1` | Verifica evento fabricado; não testa integridade referencial. |
| `RequisitosNaoFuncionaisTest` :: `rnf06T1` | Checa a mensagem da exceção de domínio, não a resposta HTTP (a verificação real está em `ApiSegurancaTest`). |
| `RegrasCriticasTest` :: `rc03T4_falhaParcialDuranteCriacao…` | Não injeta falha; só cria uma reserva. |
| `AutenticacaoAutorizacaoTest` :: `rf02T1`, `rf02T2`, `rf02T3` | Testam `autorizar`, método sem uso em produção. |
| `NotificacoesTest` :: `rf31T1`, `rf32T1` | Chamam o serviço diretamente; nenhuma transição real dispara notificação. |
| `RetiradaDevolucaoMaterialTest` :: `rf24T1` | Só confere `reservaId`; item, autor e horário não são verificados (item nem é guardado). |

## 5. Pendências ordenadas (limitadores de nota primeiro)

| Ordem | Limitador | Pendência | Esforço |
|---|---|---|---|
| P1 | Aplicação que não executa | README sem instruções para subir a aplicação (memória e PostgreSQL), sem `docker compose` e sem dados de demonstração com os 3 perfis. Documentar e configurar o Docker para Testcontainers (`DOCKER_HOST` no Colima/Rancher), senão `./mvnw -B verify` fica vermelho na máquina do avaliador. | P |
| P2 | Aplicação que não executa | Modo PostgreSQL incompleto: busca, colisão de manutenção, relatório e retirada leem `Banco.reservas`; manutenções e auditoria não são persistidas. Fazer tudo passar pelo repositório, ou declarar o modo memória como o modo de demonstração e registrar a limitação. | M–G |
| P3 | Pipeline | Pipeline existe e está verde (Sonar OK). Lacunas: **0 revisões nos PRs #1–#8** e 57/57 commits de um único autor (entregável "revisão por pares"); a etapa Sonar é pulada em silêncio se faltar `SONAR_TOKEN`; o quality gate não bloqueia o build (`sonar.qualitygate.wait`). Ativar *required reviews* na `main`. | P |
| P4 | Falha de autorização | Login devolve sempre Solicitante: guardar o perfil por usuário e semear Responsável e Administrador. Senha com BCrypt (RNF-05) e corrigir `rnf05T1`. Expiração de token. Verificação de perfil em cadastro, retirada e relatório. Remover `autorizar` sem uso ou passar a usá-lo. | M |
| P5 | Dupla reserva | Teste de sobreposição material × material (unitário e `ReservaRepositorioJdbcIT`); decidir se material é exclusivo ou por quantidade e alinhar busca e constraint; serializar manutenção × reserva (ou persistir manutenção com constraint); `rc03T4` simular falha real. | P–M |
| P6 | Integridade/auditoria | Remover o evento fabricado de `consultarPorEntidade` e reescrever `rf27T1`, `rf28T1`, `rf28T2`, `rnf09T1`; registrar autor em cadastro e manutenção; ~~gravar `REJEICAO_SOBREPOSICAO` ao recusar por conflito~~ (feito em 11/11); persistir a auditoria na mesma transação da reserva. | M |
| P7 | Regra crítica de estados | Transições `iniciarUso` (APROVADA→EM_USO), `concluir`, `registrarNaoComparecimento`, com rota, auditoria e teste; reescrever os testes vazios de `MaquinaEstadosReservaTest`; liberar as linhas `@bloqueado` depois das decisões P-05/P-07/P-08. | M |
| P8 | Requisitos ausentes na aplicação | Rotas HTTP: CRUD de salas/professores/materiais, busca de professores e materiais, `PUT /reservas/{id}` (alteração), retirada/devolução, ~~relatório (com carga horária por recurso)~~ (feito em 11/11), consulta de auditoria. Ligar `NotificacaoService` às transições com o adapter HTTP como bean. | G |
| P9 | Requisito #13 | Interface responsiva mínima (páginas estáticas consumindo a API) para os fluxos criar, aprovar/rejeitar e buscar, validada em 360/768/1920 px. | G |
| P10 | Requisito #14 e RTM | OpenAPI (springdoc) com exemplos; completar a [RTM.md](RTM.md) com todos os RFs críticos (RNF-11), usando só testes da seção 2 e não os da seção 4. | M |

## 6. Proposta de divisão até a Semana 46 (09–15/11/2026)

Integrantes identificados pelos colaboradores do repositório no GitHub. Hoje é terça, 10/11, o que deixa cerca de
5 dias úteis. A divisão abaixo é uma proposta para o grupo validar. A regra comum: **todo PR é revisado e aprovado por
outro integrante antes do merge**, o que também fecha a lacuna de revisão por pares (P3).

| Integrante | Pendências | Entrega até | Revisa PRs de |
|---|---|---|---|
| DaniloDamasio | P1 (README de execução, compose, Docker/Testcontainers), P2 (decisão do modo de demonstração e correção das leituras de `Banco.reservas`), P5 (dupla reserva: material, manutenção × reserva) | P1 qua 11/11; P5 qui 12/11; P2 sex 13/11 | renatarsilva |
| renatarsilva | P4 (perfis no login, BCrypt, expiração de token, autorização em cadastro/retirada/relatório) e P3 (required reviews, `sonar.qualitygate.wait`) | P3 ter 10/11; P4 qui 12/11 | CaiosAguiar |
| CaiosAguiar | P7 (transições EM_USO/CONCLUÍDA/NÃO_COMPARECEU) e P6 (remover auditoria fabricada, autor, `REJEICAO_SOBREPOSICAO`), com reescrita dos testes da seção 4 | P6 qua 11/11; P7 sex 13/11 | Samwel-Merces |
| Samwel-Merces | P8 (rotas de cadastro, busca, alteração, retirada, relatório, auditoria e ligação da notificação), P10 (OpenAPI) | P8 sáb 14/11; P10 sáb 14/11 | DaniloDamasio |
| Todos (dom 15/11) | P9 (interface mínima, dividida por fluxo), atualização final da RTM, novo `./mvnw -B verify` e nova rodada desta auditoria | dom 15/11 | — |

Dependências: P4 (perfis reais) e P7 (estados) liberam P8 (rotas de aprovação, retirada e relatório), então P8 deve
começar pelo CRUD e pela busca, que não dependem delas. Se o prazo apertar, P9 (interface) é a pendência a cortar
primeiro; nesse caso, registrar a decisão e o risco no relatório final de qualidade.
