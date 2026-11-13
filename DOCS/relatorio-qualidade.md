# Relatório de Qualidade: EduAloca (Organização de Recursos)

Cada afirmação deste relatório traz três partes: **Evidência** (o que foi observado e onde conferir),
**Interpretação** (o que isso significa para a meta) e **Limite** (o que a evidência não permite afirmar).
Nenhum número foi estimado. O que não pôde ser medido aparece como **não medido**, com o motivo.

---

## 1. Identificação

| Item | Valor |
|---|---|
| Sistema | EduAloca: alocação de salas, professores e materiais (perfis Solicitante, Responsável e Administrador) |
| Repositório | `github.com/DaniloDamasio/EduAloca` |
| Commit avaliado | `a670c6882687eb657b327b63f7ef8f4a2d96f149` (`main`, merge do PR #12). A árvore é idêntica à de `c55a31d` (branch `auditoria-mutacao`): as duas têm o tree `94e49e0`. |
| Data da avaliação | 2026-11-11 (UTC−3). Os runs de CI estão registrados em UTC, em 2026-11-12, entre 00:16 e 00:23. |
| Integrantes | **Danilo Damasio (DaniloDamasio), trabalho individual.** Único autor dos commits do repositório e único integrante que atuou no projeto. Sem outro integrante, a revisão por pares foi substituída por verificações automáticas que bloqueiam o merge (README, "Autoria e revisão"). |
| Autor deste relatório | Engenharia de qualidade, a partir das fontes da seção 1.1 |

### 1.1 Ambiente e fontes

| Ambiente | Detalhe |
|---|---|
| Local | Apple M4 Pro, 24 GiB, macOS 26.7.2 (25G332); JDK Temurin 21.0.9; Maven 3.9.9 (wrapper); Docker Engine 29.5.2 via Colima (Virtualization.Framework, aarch64) |
| CI | GitHub Actions, `ubuntu-latest`, Temurin 21 ([.github/workflows/ci.yml](../.github/workflows/ci.yml)): `./mvnw -B verify`, depois `sonar:sonar` e a publicação dos artefatos `test-reports` e `jacoco-report` |
| Análise estática | SonarCloud, projeto `DaniloDamasio_EduAloca`, análise da `main` em 2026-11-12T00:22:12Z, revisão `a670c68` (lida pela API pública) |

| Fonte | Uso |
|---|---|
| `./mvnw -B verify` local (2 execuções, seção 4.1) | testes, gate e cobertura |
| `target/site/jacoco/index.html` e `jacoco.xml` (local) | cobertura |
| CI run [43126611038](https://github.com/DaniloDamasio/EduAloca/actions/runs/43126611038) (`push` na `main`, `a670c68`) e run [43126029217](https://github.com/DaniloDamasio/EduAloca/actions/runs/43126029217) (`pull_request`, `c55a31d`), com logs e artefatos baixados | testes, cobertura e Sonar |
| [performance/resultado-01.md](performance/resultado-01.md) e [performance/rnf.md](performance/rnf.md) | desempenho |
| [defeitos/](defeitos/) (DEF-001 a DEF-011) | defeitos |
| [RTM.md](RTM.md) e [testes/mutacao-manual.md](testes/mutacao-manual.md) | rastreabilidade |
| [REQUISITOS/REQUISITOS.md](REQUISITOS/REQUISITOS.md) | metas e lista de requisitos críticos |

---

## 2. Escopo

### 2.1 Avaliado

- API REST Spring Boot 4.1.1: autenticação por token, matriz de autorização por perfil, busca de disponibilidade,
  criação, alteração e cancelamento de reserva, aprovação e rejeição de recurso restrito, manutenção, retirada e
  devolução, histórico e auditoria, relatório de utilização e notificação externa.
- As regras críticas do enunciado: sobreposição (RF-15/16), dupla reserva concorrente (RF-17, RNF-08), aprovação
  de restrito (RF-12/18/19), manutenção (RF-11/21/22), reserva iniciada não apagável (RF-30) e auditoria
  (RF-27/28/29).
- Persistência nos dois modos: repositório em memória (padrão da aplicação) e PostgreSQL 16 (Testcontainers).
- Processo: gate de CI, gate de cobertura, análise SonarCloud e proteção da branch `main`.

### 2.2 Fora de escopo, ou fora do alcance da evidência

| Item | Motivo |
|---|---|
| Interface de usuário e acessibilidade (RNF-14) | O repositório só versiona a API (`src/main/resources` tem apenas `application.yml` e `schema.sql`). Não há UI para avaliar. |
| Ambiente de produção | Não existe. Todas as medições são locais ou do CI. |
| Desempenho acima de 50 usuários, ou com rede e banco populado | Só existe a rodada 01 ([resultado-01.md](performance/resultado-01.md)). A rodada 02 está planejada e não foi executada. |
| Teste de segurança dinâmico (DAST), pentest e varredura de dependências (SCA) | Nenhuma ferramenta desse tipo está configurada no `pom.xml` nem no CI. **Não medido.** |
| Mutação automatizada (PIT) | Não configurada. Existe apenas a mutação manual de 33 mutantes (seção 4.4). |
| Cenários BDD que dependem de decisão do PRD (P-01, P-02, P-04 a P-08) | Sem regra definida. Estão marcados `@bloqueado` ou não têm linha executável ([RTM.md](RTM.md)). |

---

## 3. Estratégia de teste por nível

Números tirados dos relatórios Surefire/Failsafe da execução local, iguais aos do CI.

| Nível | O que cobre | Classes (casos) | Ferramenta | Onde roda |
|---|---|---|---|---|
| Unidade e domínio | Regras de serviço, máquina de estados, sobreposição, manutenção, auditoria, retirada e devolução, relatórios, notificação | 19 classes (188 casos), p. ex. `RegrasCriticasTest` (28), `HistoricoAuditavelReservaTest` (29), `MaquinaEstadosReservaTest` (13) | JUnit 5 | Surefire, local e CI |
| API (caixa-preta HTTP) | Matriz de autorização com token do login real, ProblemDetail, validação de entrada, histórico, relatório | 6 classes (164 casos), sendo `MatrizAutorizacaoApiTest` responsável por 114 | Spring Boot Test | Surefire, local e CI |
| Aceitação (BDD) | Aprovação, bloqueio por manutenção e estados da reserva | `AprovacaoBloqueioFeatureTest` (21 casos, 3 pulados por `@bloqueado`) | Cucumber | Surefire, local e CI |
| Integração externa | Adaptador HTTP de notificação: 404, 500 com retry, timeout, corpo inválido, vazamento de token | `IntegracaoNotificacaoExternaTest` (6) | WireMock (porta dinâmica, offline) | Surefire, local e CI |
| Integração com banco | Constraint de exclusão GiST, concorrência em 1 e 2 JVMs, histórico transacional e imutável | 4 classes IT (14 casos) | Testcontainers, PostgreSQL 16 | Failsafe, local (com Docker) e CI |
| Outros unitários de suporte | Repositório JDBC (mapeamento) e seed do perfil `perf` | 2 classes (5 casos) | JUnit 5 | Surefire |
| Desempenho | Busca e criação sob disputa, 50 usuários, 5 min estáveis | `perf/reservas.jmx` | JMeter 5.6.3 | **Somente local e manual**, fora do CI |
| Eficácia dos testes | Quebra manual de regra crítica no código de produção | 33 mutantes | `git restore` + Maven | Local, rodada única (2026-11-11) |
| Análise estática | Bugs, vulnerabilidades, hotspots e code smells | — | SonarCloud | CI |
| TDD | Ciclos red/green/refactor com commits citados | — | — | [testes/evidencia-tdd.md](testes/evidencia-tdd.md) e coluna "Commit" da RTM |

**Observação sobre a contagem:** o Maven informa `Tests run: 387, Skipped: 3` para o Surefire, mas a soma dos
29 arquivos `TEST-*.xml` dá 384 casos. A diferença de 3 aparece tanto no local quanto no artefato do CI. A causa
não foi investigada. Este relatório usa o número do Maven como total e os arquivos XML para a divisão por nível.

---

## 4. Resultados comparados às metas

### 4.0 Quadro-resumo

| Meta | Alvo | Medido | Situação |
|---|---|---|---|
| Cobertura de linhas (RNF-10) | ≥ 80% | CI: 1000/1036 = **96,53%**; local: 994/1031 = 96,41% | **Atende** |
| Cobertura de ramos (RNF-10) | ≥ 70% | CI e local: 356/409 = **87,04%** | **Atende** |
| Requisitos críticos na RTM com teste e evidência (RNF-11) | 100% | **20 de 32 = 62,5%** (auditoria de [mutacao-manual.md §4](testes/mutacao-manual.md), conferida neste relatório) | **Não atende** |
| Zero bugs ou vulnerabilidades críticas (meta do enunciado) | 0 | SonarCloud `main` (`a670c68`): 0 bugs, 0 vulnerabilidades, 0 hotspots | **Atende, dentro do alcance do Sonar** |
| RNF-07, critério literal: "0 issues Critical/Blocker" | 0 | **2 issues BLOCKER** (code smell `java:S2699` em código de teste) e 0 CRITICAL | **Não atende** pelo texto do RNF-07 |

### 4.1 Build e testes

**A-01. O `./mvnw -B verify` sem variáveis de ambiente falha nesta máquina.**
- *Evidência:* 1ª execução local (2026-11-11, ~21:20): Surefire com 387 casos, 0 falhas e 3 pulados; Failsafe com
  4 classes IT em erro, `IllegalStateException: Could not find a valid Docker environment`. Resultado:
  `BUILD FAILURE`. O JaCoCo `report`/`check` não chegou a rodar.
- *Interpretação:* não é defeito do produto. O Testcontainers não acha o socket do Colima sem configuração, o que
  já estava documentado em [mutacao-manual.md §1](testes/mutacao-manual.md). Mesmo assim, o comando de build do
  README não é reproduzível "como está" num macOS com Colima.
- *Limite:* não foi testado em outra máquina local nem com Docker Desktop.

**A-02. Com Docker configurado, o build passa localmente e no CI.**
- *Evidência:* 2ª execução local com
  `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`:
  Surefire 387 (0 falhas, 3 pulados), Failsafe 14 (0 falhas), `All coverage checks have been met`,
  `BUILD SUCCESS`, 14,6 s. CI run 43126611038 (`a670c68`) e run 43126029217 (`c55a31d`): os mesmos 387/3 e 14/0,
  gate atendido, todos os passos com `success`.
- *Interpretação:* a suíte inteira, incluindo as regras críticas contra PostgreSQL real, está verde no commit
  avaliado.
- *Limite:* uma execução por ambiente. Não houve repetição para detectar teste intermitente, exceto a dos ITs de
  concorrência (5 execuções seguidas, registradas em [DEF-001](defeitos/DEF-001-dupla-reserva-entre-instancias.md)).
  Os 3 casos pulados (linhas `@bloqueado` de BDD-R4-01) não são evidência de nada.

### 4.2 Cobertura (meta: 80% de linhas e 70% de ramos)

**A-03. As duas metas de cobertura são atendidas com folga, e o gate as impõe no build.**
- *Evidência:* `jacoco.xml` do artefato `jacoco-report` do run 43126611038: LINE 1000 cobertas e 36 perdidas
  (96,53%); BRANCH 356 cobertos e 53 perdidos (87,04%); 68/68 classes. Local (`target/site/jacoco/index.html`,
  linha "Total"): instruções 96%, ramos 87%; `jacoco.xml` local com 994/1031 linhas (96,41%) e 356/409 ramos. O
  SonarCloud da `main` mostra `line_coverage` 96,5 e `branch_coverage` 87,0. O `pom.xml` define a regra
  `BUNDLE` com `LINE ≥ 0.80` e `BRANCH ≥ 0.70`, sem `<excludes>`. O relatório roda na fase `verify`, depois do
  Failsafe, então soma unitários e ITs.
- *Interpretação:* RNF-10 atendido. Os pacotes abaixo de 80% em linha ou ramo são `com.edualoca.api` (ramos 9/15 =
  60,0%), `com.edualoca.api.recurso` (ramos 3/6 = 50,0%), `com.edualoca.domain.auditoria` (ramos 3/4 = 75,0%)
  e `com.edualoca` (linhas 1/3, só a classe `main`). Os ramos menos testados estão na camada HTTP de erro e no
  cadastro de recursos.
- *Limite:* cobertura mede execução, não verificação. A mutação manual mostra isso: antes do DEF-011, 3 testes
  de auditoria cobriam o código e passavam com qualquer implementação. A contagem de linhas difere entre CI
  (1036) e local (1031). A causa não foi investigada, e nas duas o resultado fica acima da meta. O RNF-10 pede
  "relatório JaCoCo da branch principal": o run 43126611038 é exatamente a `main`.

### 4.3 Rastreabilidade (meta: 100% dos requisitos críticos na RTM)

**A-04. Existem 32 requisitos críticos.**
- *Evidência:* `grep -c "| Crítico |" DOCS/REQUISITOS/REQUISITOS.md` retorna 32. São 23 RFs (01–06, 11, 12,
  14–19, 21, 22, 24, 25, 27–30, 38) e 9 RNFs (04–08, 10–12, 15).
- *Interpretação:* é o denominador da meta.
- *Limite:* a classificação "Crítico" é do próprio autor. Este relatório não a reavaliou.

**A-05. 20 de 32 requisitos críticos (62,5%) têm linha na RTM com risco, teste localizável e execução verde no CI.
A meta de 100% não é atendida.**
- *Evidência:* [mutacao-manual.md §4](testes/mutacao-manual.md) e o fechamento de [RTM.md](RTM.md): 19/32 em
  `ee540c5` e 20/32 em `d69901d`. Recontei a tabela da §4 e cheguei a 20 com evidência e 12 sem: **RF-04, RF-05,
  RF-06, RF-18, RF-24, RF-25, RNF-05, RNF-07, RNF-10, RNF-11, RNF-12, RNF-15**. Os testes que essas 20 linhas
  citam rodaram verdes no CI do commit avaliado (A-02). A própria RTM diz, no topo, que não representa 100%
  dos críticos.
- *Interpretação:* RNF-11 **não atendido**. Parte das 12 lacunas é de documentação, não de teste: RNF-10 e RNF-12
  estão demonstrados neste relatório (A-03 e A-09) e RF-04/05/06 e RF-24/25 têm classes de teste verdes
  (`CadastroConsultaTest` com 11 casos e `RetiradaDevolucaoMaterialTest` com 14), mas sem linha na RTM nem
  auditoria por mutação. Outras são lacunas reais: RF-18 não tem regra no código (A-12), RNF-05 está
  contrariado pela implementação (A-11) e RNF-07 não atende pelo critério literal (A-08).
- *Limite:* este relatório não auditou `CadastroConsultaTest` nem `RetiradaDevolucaoMaterialTest`. Não se pode
  dizer que eles verificam RF-04/05/06/24/25, só que existem e passam.

### 4.4 Eficácia dos testes (mutação manual)

**A-06. Os testes das regras críticas detectaram 30 de 33 mutantes (90,9%). As 3 lacunas foram fechadas.**
- *Evidência:* [mutacao-manual.md §3](testes/mutacao-manual.md): 33 quebras no código de produção, uma por vez,
  nas regras de sobreposição, dupla reserva, aprovação, manutenção, autorização e auditoria. Lacunas: D3 (lock do
  serviço no modo em memória), A2 (material restrito) e U8 (auditoria de cadastro de manutenção). Os testes novos
  `ConcorrenciaReservaEmMemoriaTest`, `AprovacaoRecursosRestritosTest.rf12T2_…` e `AuditoriaCadastroTest` matam
  os três (3/3 execuções cada) e estão verdes no CI (A-02).
- *Interpretação:* para as regras críticas, os testes verificam o comportamento e não só executam o código.
- *Limite:* é um escore manual, com mutantes escolhidos pelo próprio autor. Não é amostra aleatória nem exaustiva,
  e **não é comparável a um escore PIT**. O escore depois das correções (33/33) vale para esses 33 mutantes, não
  para o código como um todo. Mutação automatizada: **não medida** (sem ferramenta configurada).

### 4.5 Análise estática (meta: zero bugs ou vulnerabilidades críticas)

**A-07. O SonarCloud da `main` não reporta nenhum bug, vulnerabilidade ou hotspot.**
- *Evidência:* API pública do SonarCloud, `branch=main`, análise de 2026-11-12T00:22:12Z, revisão `a670c68`:
  `bugs=0`, `vulnerabilities=0`, `security_hotspots=0`; `issues/search` com `types=BUG` e
  `types=VULNERABILITY` retorna `total 0`; quality gate `OK`. No PR #12, também 0 issues.
- *Interpretação:* a meta "zero bugs ou vulnerabilidades críticas" está atendida **dentro do que o Sonar
  detecta**.
- *Limite:* o Sonar não detectou o armazenamento de senha em texto plano (A-11), que é um problema de segurança
  real. "Zero vulnerabilidades no Sonar" não quer dizer "zero vulnerabilidades". Não houve SCA nem DAST (seção
  2.2).

**A-08. Há 2 issues BLOCKER, o que reprova o critério literal do RNF-07.**
- *Evidência:* `issues/search` na `main` com `severities=BLOCKER` retorna 2 issues `CODE_SMELL` `java:S2699`
  ("Add at least one assertion to this test case") em `AutenticacaoAutorizacaoTest.java`, linhas 73
  (`rf02T1_administrador_deveSerAutorizado`, que chama `autorizar` sem nenhum `assert`) e 111
  (`rf03T1_solicitanteAlteraPropriaReserva_devePermitir`, que cancela e não confere o estado). `CRITICAL`
  retorna 0. Também há 2 issues MAJOR `java:S8786` (regex super-linear) em `ConcorrenciaReservaIT.java:132` e
  `ConcorrenciaReservaMultiInstanciaIT.java:150`. Os 4 issues estão em código de teste.
- *Interpretação:* o RNF-07 se chama "0 vulnerabilidades Crítica/Bloqueadora", mas o critério de aceite diz
  "0 issues Critical/Blocker". Pelo nome, atende. Pelo critério, **não atende**. Este relatório adota o critério
  de aceite, que é o texto verificável. Os dois BLOCKER também apontam testes fracos: os dois só falham se for
  lançada uma exceção.
- *Limite:* não verifiquei se os issues foram triados ou aceitos no painel do SonarCloud. A consulta usou
  `resolved=false`.

### 4.6 Desempenho

> **Resultado vigente: [rodada 02](performance/resultado-02.md)** (A-23, seção 9). A A-09a abaixo descreve a rodada 01, como
> estava no commit avaliado `a670c68`, e fica como histórico.

**A-09a. Na rodada 01, RNF-01, RNF-02 e RNF-03 foram atendidos nas condições medidas.**
- *Evidência:* [resultado-01.md §3–4](performance/resultado-01.md), fase estável de 300 s com 50 usuários:
  `GET /salas/disponiveis` p95 = 2 ms (n = 7.938, 0 erros); `POST /reservas` p95 = 8 ms (n = 1.987, 0 erros
  reais); 397,4 criações/min; 0 respostas 5xx; 217 janelas disputadas, cada uma com exatamente 1 reserva aceita
  e 0 duplicatas no banco. Conferi os hashes SHA-256 (16 primeiros caracteres) de `perf/resultados/rodada-01.jtl`
  (`dcfeada7c825bdee`), `perf/reservas.jmx` (`e07b3d46b84c38e6`) e `perf/usuarios.csv` (`a4b336ba2b8cb3d9`), e
  eles batem com a §1.4.
- *Interpretação:* não há sinal de problema de desempenho com 50 usuários. A vazão foi limitada pelo modelo de
  carga (lei de Little), não pelo sistema, então **a rodada não identifica gargalo nem capacidade máxima**.
- *Limite:* (1) o jar medido veio de `0cf9518` **mais alterações não commitadas**, então o resultado não é
  reproduzível pelo git nem corresponde ao commit avaliado aqui; (2) foi uma rodada só, com gerador, aplicação e
  banco na mesma máquina, sem rede e com o banco quase vazio; (3) CPU, memória, GC e pool Hikari **não foram
  medidos**; (4) as metas propostas RNF-0x-P ainda não foram validadas pelo autor; (5) o teste não roda no CI.
  Nada disso permite afirmar algo sobre produção.

### 4.7 Processo (CI)

**A-09. O CI bloqueia merge na `main`, e todos os PRs mergeados desde a criação do workflow têm CI verde.**
- *Evidência:* `gh api …/branches/main/protection`: check obrigatório `Build e Testes`, `strict: true`,
  `enforce_admins: true`. `gh pr list --state merged`: PRs #4 a #12 com `Build e Testes=SUCCESS`. Os PRs #1 a
  #3 não têm checks. O `ci.yml` foi adicionado em 2026-10-27 (`8646a6d`), e o PR #4 foi mergeado nesse mesmo
  dia. O SonarCloud aparece como check a partir do PR #5.
- *Interpretação:* RNF-12 é atendido desde que o CI existe. Falta registrá-lo na RTM (A-05).
- *Limite:* pelo texto literal do RNF-12 ("100% dos PRs mesclados"), a taxa é 9 de 12. Os 3 sem CI são
  anteriores à existência do pipeline. O passo do Sonar é condicional (`if: env.SONAR_TOKEN != ''`), então
  um PR sem o segredo passaria pelo gate sem análise estática. Não verifiquei se isso já aconteceu.

---

## 5. Defeitos por severidade e situação

**Fonte:** campos "Severidade" e "Status" de cada arquivo em [defeitos/](defeitos/). A escala usada no projeto é
Alta, Média e Baixa. **Nenhum defeito foi classificado como "Crítico"**, porque a escala não tem esse nível.

| Severidade | Corrigido | Aberto | Defeitos |
|---|---|---|---|
| Alta | 5 | 0 | [DEF-001](defeitos/DEF-001-dupla-reserva-entre-instancias.md) (dupla reserva entre instâncias), [DEF-003](defeitos/DEF-003-login-emite-sempre-solicitante.md) (login sempre Solicitante), [DEF-004](defeitos/DEF-004-alteracao-de-reserva-sem-rota.md) (alteração sem rota), [DEF-010](defeitos/DEF-010-responsavel-nao-competente-decide-reserva.md) (Responsável de outro bloco decide), [DEF-011](defeitos/DEF-011-auditoria-inventa-evento-inexistente.md) (auditoria inventa evento) |
| Média | 3 | 0 | [DEF-002](defeitos/DEF-002-erros-fora-do-padrao-problemdetail.md) (erro fora do ProblemDetail), [DEF-005](defeitos/DEF-005-cancelamento-revela-id-inexistente.md) (enumeração de ids), [DEF-009](defeitos/DEF-009-responsavel-e-administrador-criam-reserva.md) (Responsável/Admin criam reserva) |
| Baixa | 3 | 0 | [DEF-006](defeitos/DEF-006-exclusao-de-solicitada-responde-405.md) (405 em rota mapeada), [DEF-007](defeitos/DEF-007-capacidade-minima-negativa-aceita.md) (capacidade negativa), [DEF-008](defeitos/DEF-008-parametro-ausente-sem-nome-do-campo.md) (mensagem sem nome do campo) |
| **Total** | **11** | **0** | |

**A-10. Os 11 defeitos registrados estão corrigidos, com reteste por teste automatizado verde no CI.**
- *Evidência:* todos os arquivos têm Status "Corrigido (reteste aprovado em 10/11 ou 11/11/2026)". Os testes que
  detectam cada um (`ConcorrenciaReservaMultiInstanciaIT`, `MatrizAutorizacaoApiTest`,
  `AuditoriaTest.def011_…`) estão entre os casos verdes do run 43126611038.
- *Interpretação:* não há defeito **registrado** em aberto. Pelo menos três dos defeitos Alta (DEF-001, DEF-010
  e DEF-011) violavam regras críticas do enunciado. Numa escala com nível "Crítico", provavelmente seriam
  classificados assim. Mesmo assim, "zero defeitos críticos" é verdade só porque a escala não tem esse nível.
- *Limite:* os defeitos foram encontrados em 10 e 11/11/2026, num intervalo curto. A ausência de defeito aberto
  reflete o que foi procurado, não prova a ausência de defeitos. A seção 6 lista achados **não registrados**.

---

## 6. Riscos residuais

Achados que não estão em [defeitos/](defeitos/), mais limitações conhecidas. A severidade aqui é proposta por este
relatório, na escala do projeto.

**A-11. Senhas ficam em texto plano na memória, contrariando o RNF-05 (proposta: Alta, segurança).**
- *Evidência:* `Banco.credenciais` (`domain/comum/Banco.java:34`, seed nas linhas 96–97 e 110) é um
  `Map<String,String>` de e-mail para senha em claro. `AutenticacaoService.autenticar` (linhas 12–13) compara com
  `senhaCadastrada.equals(senha)` e só depois calcula `SenhaHasher.hash`, que é SHA-256 sem sal, para preencher
  `Usuario`. O `schema.sql` não tem coluna de senha.
- *Interpretação:* o critério literal do RNF-05 ("0% de senhas em texto plano **no schema do banco**") é atendido
  por vacuidade, porque não existe a coluna. A intenção do requisito ("nunca texto plano", "ex.: BCrypt") **não**
  é atendida. A comparação com `equals` também não é em tempo constante. Nem o Sonar (A-07) nem os testes
  detectam o problema.
- *Limite:* a persistência de usuários é em memória e as credenciais são de seed. Não foi feita avaliação de
  exploração.

**A-12. RF-18 não tem regra no código, e o teste que leva seu nome não o verifica.**
- *Evidência:* [mutacao-manual.md §2.3](testes/mutacao-manual.md): `rf18T1` chama `aprovar` com um UUID
  inexistente e recebe 409 por "reserva inexistente". Não existe transição para `EM_USO` (pendência P-05).
- *Interpretação:* a regra crítica "restrita passa por `APROVADA` antes de `EM_USO`" não pode ser violada hoje
  porque a transição não existe. Quando ela for implementada, não haverá teste protegendo a regra. Além disso,
  `rf18T1` dá falsa evidência ao ficar verde.
- *Limite:* depende da decisão do PRD (P-05).

**A-13. A busca de professores e materiais não exclui recurso em manutenção (candidato a DEF-012, não registrado).**
- *Evidência:* `PesquisaRecursoService.disponivel()` (linhas 57–64) só consulta manutenção quando o recurso é
  `Sala`. `buscarMateriais` (linhas 41–48) não consulta manutenção. O código está igual em `a670c68`.
- *Interpretação:* RF-11 está atendido só para salas. Para professor e material, o sistema contraria o critério
  "0% de recursos indisponíveis retornados". A criação da reserva continua bloqueada (mutante M5 morto), então o
  usuário vê o recurso na busca e recebe 409 ao reservar.
- *Limite:* hoje a API só expõe `/salas/disponiveis`. O defeito só aparece pelo serviço, não por uma rota HTTP.

**A-14. A auditoria de cadastro grava `autorId = null`.**
- *Evidência:* [mutacao-manual.md §2.6](testes/mutacao-manual.md) e o README: `CadastroRecursoService` não recebe
  o usuário.
- *Interpretação:* o RF-28 exige só a entrada de auditoria, então é atendido. Mas a pergunta "quem cadastrou ou
  bloqueou" não tem resposta, e esse é um dos usos da auditoria descritos pela persona Administrador.
- *Limite:* nenhum.

**A-15. Outros riscos.**
- Desempenho sem evidência reproduzível no commit avaliado e sem teste de capacidade (A-09a).
- Mitigação da dupla reserva entre instâncias: depende da constraint `ex_reserva_recurso_sem_sobreposicao`
  ([ADR-009](adr/ADR-009-exclusao-gist-tstzrange.md)). No modo em memória, que é o padrão, só o `ReentrantLock`
  protege, e ele vale dentro de uma JVM. Implantar o modo em memória com mais de uma instância reabriria o
  DEF-001.
- Build local frágil: o build depende de variáveis de ambiente que não estão documentadas no README (A-01).
- Cenários bloqueados por decisão do PRD: BDD-R1-09, R2-05, R3-07, R4-06 e as linhas de R4-01/R4-02 (iniciar uso,
  concluir, não comparecimento) ([RTM.md](RTM.md)).
- RF-04/05/06 e RF-24/25: os testes existem e passam, mas a eficácia não foi auditada (A-05).

---

## 7. Recomendação

**Situação das metas:** cobertura **atendida** (96,5% linhas e 87,0% ramos, contra 80% e 70%). Zero bugs e
vulnerabilidades no Sonar **atendido**, dentro do alcance do Sonar. RTM com 100% dos críticos **não atendida**
(62,5%). RNF-07 **não atendido** pelo critério literal (2 BLOCKER em código de teste). RNF-05 **contrariado** pela
implementação, embora sem detecção automática.

**Recomendação: aceite condicionado.** As regras centrais do enunciado (sobreposição, dupla reserva concorrente em
PostgreSQL com 1 e 2 instâncias, aprovação de restrito, manutenção, autorização por perfil, histórico imutável)
têm testes verdes no CI do commit avaliado e passaram pela mutação manual. Isso basta para demonstrar o núcleo do
sistema. **Não** basta para declarar que as metas de qualidade do projeto foram cumpridas: a meta de
rastreabilidade não foi atingida, e há um problema de segurança conhecido e não registrado.

**Condições, em ordem de prioridade:**

1. Registrar A-11 como defeito e armazenar senha com hash adaptativo e sal (p. ex. BCrypt), com teste que
   verifique que a senha em claro não fica armazenada. Fecha RNF-05.
2. Registrar A-13 como DEF-012 e corrigir a busca de professor e material em manutenção, com teste. Completa
   RF-11.
3. Corrigir os 2 issues `java:S2699` (dar asserção a `rf02T1` e `rf03T1`) e as 2 regexes
   `java:S8786`. Fecha RNF-07 pelo critério literal.
4. Abrir na RTM as linhas que faltam, com risco e evidência de CI: RNF-10 e RNF-12 (já demonstrados aqui),
   RF-04/05/06 e RF-24/25 (auditar os testes existentes por mutação), RNF-15 (revisão de pacotes), RNF-05 e
   RNF-07 (depois dos itens 1 e 3). Remover ou renomear `rf18T1` até a decisão da P-05.
5. Executar a rodada 02 de desempenho a partir de um commit limpo ([resultado-01.md §6](performance/resultado-01.md)).
6. Documentar no README as variáveis `DOCKER_HOST` e `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` para Colima.

**Critério para reavaliar:** um novo run de CI verde na `main` com os itens 1 a 4 feitos, e a RTM com 32 de 32
críticos com evidência.

---

## 8. Correções após a avaliação (2026-11-12)

As seções 1 a 7 descrevem o commit `a670c68` e **não foram alteradas**. Esta seção registra o que mudou no branch
`correcoes-relatorio-qualidade` (PR [#13](https://github.com/DaniloDamasio/EduAloca/pull/13), CI run [43238556274](https://github.com/DaniloDamasio/EduAloca/actions/runs/43238556274), commit `eb4b3af`), com o mesmo formato: evidência, interpretação e limite.

### 8.1 Resultado por meta

| Meta | `a670c68` | Depois das correções | Situação |
|---|---|---|---|
| Linhas ≥ 80% | 96,53% (CI) | CI: 1115/1150 = **96,96%** (local: 1112/1147 = 96,95%) | Atende |
| Ramos ≥ 70% | 87,04% (CI) | CI e local: 405/465 = **87,10%** | Atende |
| Críticos na RTM com evidência | 20/32 | **32/32** (31 por teste verde no CI; RNF-07 pela análise do SonarCloud da `main` em `a9d419f`) | **Atende** |
| Zero bugs/vulnerabilidades críticas | 0 no Sonar, mas senha em claro (A-11) | DEF-013 corrigido; SonarCloud do PR #13: gate OK, 0 issues, 0 hotspots | Ver A-19 |
| RNF-07 (0 Blocker/Critical) | 2 BLOCKER | SonarCloud da `main` (`a9d419f`, 2026-11-12T18:04:40Z): **0 issues** de qualquer severidade; gate OK | **Atende** |

### 8.2 Afirmações

**A-16. Quatro defeitos novos registrados e corrigidos, com red e green separados.**
- *Evidência:* [DEF-012](defeitos/DEF-012-busca-oferece-professor-e-material-em-manutencao.md) (Média, busca),
  [DEF-013](defeitos/DEF-013-senha-armazenada-em-texto-plano.md) (Alta, senha em claro),
  [DEF-014](defeitos/DEF-014-retirada-nao-vincula-item.md) (Alta, retirada sem item) e
  [DEF-015](defeitos/DEF-015-crud-de-recursos-incompleto.md) (Alta, CRUD incompleto). Cada um tem commit red com o
  teste falhando (ou não compilando, quando o método nem existia) e commit green.
- *Interpretação:* A-11 e A-13 do relatório foram resolvidos. DEF-014 e DEF-015 apareceram na auditoria de RF-04/05/06
  e RF-24/25, que antes não tinham sido auditados (A-05). Total geral: 15 defeitos, 15 corrigidos (Alta 8, Média 4,
  Baixa 3).
- *Limite:* os defeitos novos saíram de leitura de código e mutação no mesmo dia da correção. Nenhum foi visto por
  usuário. As premissas adotadas estão em cada DEF (p. ex. item da retirada = material da reserva; competências do
  professor podem ser vazias).

**A-17. RF-18 passou a ter regra, a partir de uma decisão de produto.**
- *Evidência:* P-07 decidida em 12/11/2026 pelo autor do projeto: início de uso manual, por Responsável competente ou
  Administrador ([bdd-aprovacao-bloqueio.md](testes/bdd-aprovacao-bloqueio.md)). `ReservaService.iniciarUso` (red
  `820e634`, green `a9fd840`), `InicioUsoReservaTest` com 6 casos, mutantes I1–I5 mortos. O teste
  `fluxoReal_permiteRetiradaSoDepoisDoInicioDoUso` percorre SOLICITADA → APROVADA → EM_USO → retirada sem `setEstado`.
- *Interpretação:* a regra crítica "não pula para EM_USO sem APROVADA" está implementada e protegida. O falso
  `rf18T1` virou `rf19T5`.
- *Limite:* `EM_USO → CONCLUIDA`, não comparecimento (P-05/P-08) e cancelamento em uso (P-06) continuam abertos. A
  linha BDD-R4-01 "iniciar uso" continua `@bloqueado` na feature. Não há rota HTTP para iniciar o uso.

**A-18. Eficácia dos testes nos requisitos antes sem evidência: 9 de 16 mutantes antes; 44 de 44 depois.**
- *Evidência:* [mutacao-manual.md §6](testes/mutacao-manual.md). Antes: C1, C3, C5, C7, R1, R3 e R4 sobreviviam.
  Depois: C1–C24, R1–R11, I1–I5, P1–P3 e Q1 mortos.
- *Interpretação:* os testes de cadastro e retirada passaram a conferir o efeito (consulta, campos gravados), e não
  só o objeto devolvido.
- *Limite:* o mesmo da A-06. Os mutantes foram escolhidos por quem escreveu os testes.

**A-19. A senha agora é armazenada com PBKDF2-HMAC-SHA256 com sal; o custo apareceu no tempo da suíte.**
- *Evidência:* `ArmazenamentoSenhaTest` (4 casos; P1–P3 mortos). O `verify` local passou de 14,6 s (A-02) para
  30,4 s. Cada hash custa ~48 ms nesta máquina (600.000 iterações, medido com 5 repetições).
- *Interpretação:* RNF-05 atendido pela intenção, não só pela letra (A-11). A resposta para e-mail inexistente gasta
  o mesmo custo de derivação.
- *Limite:* tempo constante entre conta existente e inexistente **não foi medido**, só construído. Não há limite de
  tentativas no login. A rodada 01 de desempenho é anterior a esta mudança; com o perfil `perf`, a subida passa a
  calcular 50 hashes.

**A-20. RNF-15 tem verificação automática, e ela achou uma violação real.**
- *Evidência:* `ArquiteturaCamadasTest` falhou no red (`d0f8ed4`) apontando os 5 imports do Spring Web em
  `domain/notificacao/NotificacaoHttpAdapter`. O adaptador foi movido para `com.edualoca.integracao` (`cbe3f53`). A
  mutação Q1 (import da API no domínio) é detectada.
- *Interpretação:* o domínio não depende mais de framework web, JDBC, API nem adaptadores.
- *Limite:* a regra lê `import`, então não pega nome totalmente qualificado no corpo do código nem reflexão.
  `com.edualoca.perf` e a classe `main` estão fora das regras.

**A-21. Build e documentação.**
- *Evidência:* `./mvnw -B verify` local com Docker no `HEAD` do branch: 429 unitários (3 pulados), 14 ITs, gate
  atendido. O README documenta as variáveis do Colima (A-01) e a nota de segurança foi corrigida: dizia que não
  existia login e que os erros saíam como `{"mensagem"}`.
- *Evidência (CI):* run 43238556274 (`eb4b3af`): 429 unitários (3 pulados) e 14 ITs, 0 falhas, todos os passos `success`, incluindo o SonarCloud. Os 9 testes novos ou alterados aparecem verdes no artefato `test-reports`.
- *Limite:* a contagem de linhas difere entre CI (1150) e local (1147), como na A-03. A causa não foi investigada.

### 8.3 Riscos residuais que continuam

- Eventos de cadastro com `autorId = null` (A-14): continua.
- Sem rota HTTP para cadastro, retirada/devolução e início de uso. A restrição por perfil existe no domínio só para o
  início de uso.
- Sem rate limit nem expiração de token. Contas de demonstração com senha fixa no código.
- Desempenho: rodada 02 não executada (A-09a).
- Redução de quantidade de material abaixo do já reservado é aceita (DEF-015, limite).

### 8.4 Recomendação revisada

**Aceite.** A condição externa foi confirmada depois do merge do PR #13: o CI da `main` (run
[43275743971](https://github.com/DaniloDamasio/EduAloca/actions/runs/43275743971), `a9d419f`) tem 429 unitários e 14
ITs sem falhas, linhas 1115/1150 = 96,96% e ramos 405/465 = 87,10%, e a análise do SonarCloud da `main` no mesmo commit
tem 0 issues de qualquer severidade, 0 bugs, 0 vulnerabilidades e 0 hotspots, com gate OK. As quatro metas estão
atendidas com evidência: cobertura, 32/32 críticos na RTM, zero bugs/vulnerabilidades críticas e RNF-07.

*Limite:* "aceite" vale para as metas do projeto, medidas no CI e na análise estática. Os riscos residuais da §8.3
continuam, e o desempenho só foi medido na rodada 01, anterior a estas correções.

---

## 9. Estado em 2026-11-12 (noite): entregas da branch `entrega-final-pendencias`

As seções 1 a 8 não foram alteradas, exceto a linha "Integrantes" da seção 1 (trabalho individual). Esta seção separa o
que foi **medido** do que está **só na árvore de trabalho**.

### 9.1 Medido

**A-22. A suíte inteira passa no commit `38bbc04` (com uma alteração local em `MaquinaEstadosReservaTest`).**
- *Evidência:* `./mvnw -B verify` local com Colima: 468 unitários (3 pulados por `@bloqueado`) e 16 ITs (PostgreSQL e E2E
  em Chromium), 0 falhas, `All coverage checks have been met`; `jacoco.xml`: linhas 1303/1343 = **97,02%**, ramos
  458/524 = **87,40%**.
- *Interpretação:* as metas de cobertura continuam atendidas depois de notificação integrada, conclusão, não
  comparecimento manual, edição de cadastro e E2E.
- *Limite:* essa execução **coincidiu com o cenário 50-b da rodada 02 de carga** (cerca de 40 s de CPU disputada e o jar
  em `target/` regravado com o mesmo conteúdo). O 50-b foi mantido como **suspeito**, e as três repetições de 50 usuários
  foram refeitas na tomada com o mesmo jar, sem interferência (A-23). O resultado da suíte não é afetado.
  Ainda não há run de CI para esse commit (a branch não foi publicada).

**A-23. Rodada 02 de carga: atende com margem até 400 usuários, sem saturar.**
- *Evidência:* [performance/resultado-02.md](performance/resultado-02.md), **resultado vigente** (a rodada 01 vira
  histórico). Jar do commit `38bbc04` (SHA-256 `d66c57130a04e87f`), PostgreSQL, banco e aplicação recriados por cenário.
  São 8 cenários (50 ×3, 100, 200, 400, e 200 e 400 com a disputa em 10 salas) mais 3 repetições de 50 na tomada. Em todos:
  0 erros reais, 0 respostas 5xx, consulta com p95 de 1–3 ms, criação com p95 de 8–12 ms, e **6.308 janelas disputadas
  nos 8 cenários principais, todas com exatamente uma reserva aceita e 0 pares sobrepostos no banco**. Vazão de até
  264,5 req/s (3.174 criações/min). CPU da aplicação no máximo 47,5% de um núcleo, pool Hikari com no máximo 1 conexão
  ativa e nenhuma espera, maior pausa de GC de 13,7 ms.
- *Interpretação:* RNF-01, RNF-02, RNF-03 e RF-17 sob carga atendem com duas ordens de grandeza de folga. A hipótese da
  rodada 01 (o lock por recurso seria o primeiro gargalo) **não se confirmou nessa faixa**: o sistema não saturou, e a
  vazão foi limitada pelo tempo de pensamento do modelo de carga (lei de Little: 266,6 previstos contra 264,5 medidos).
- *Limite:* gerador, aplicação e banco na mesma máquina, com a CPU do JMeter não medida; ponto de saturação não
  encontrado; na variante de 10 salas, as respostas de consulta ficaram 2,9 vezes maiores, uma variável a mais nessa
  comparação; o 50-b teve um build em paralelo (sem efeito visível nos números). A primeira tentativa (16:24–18:03)
  foi descartada por suspensão da máquina (tampa fechada). Sem rede e com banco vazio.

**A-24. Suíte conjunta (esta branch + as entregas da 9.2): 4 erros, todos num teste novo; integração e E2E verdes.**
- *Evidência:* `./mvnw -B verify` local em 12/11 (~19:24), depois da rodada 02, sobre a árvore de trabalho completa.
  Unitários: 517 casos, 513 verdes e 4 erros em `SobreposicaoMaterialTest.mesmoMaterial` (o `@CsvSource` passa horas como
  `"08"`/`"09"`, que o JUnit não converte para `int`). Com `-Dmaven.test.failure.ignore=true`: integração e E2E **18/18
  verdes**, gate JaCoCo atendido (linhas 1490/1552 = **96,01%**, ramos 556/650 = **85,54%**). As 3 linhas de BDD-R4-01
  antes `@bloqueado` rodaram (0 casos pulados).
- *Interpretação:* as entregas da 9.2 passam a estar **verificadas localmente**, exceto `SobreposicaoMaterialTest`, que
  precisa de correção nos dados de entrada. Como o build parou nos unitários, o `verify` padrão continua vermelho até essa
  correção.
- *Limite:* execução local, sem CI nem SonarCloud (a branch não foi publicada).

### 9.2 Na árvore de trabalho, sem execução

Feito sem rodar build, aplicação, Docker ou navegador, para não interferir na rodada 02. Os arquivos Java compilam pelo
compilador do IDE (0 erros); o JavaScript passa em `node --check`; o `openapi.yaml` foi lido por um parser YAML e tem
39 operações para 39 rotas, sem referências quebradas. **Nada disso substitui a execução da suíte.**

| Entrega | Requisito | Teste escrito |
|---|---|---|
| Não comparecimento automático no fim do horário (tarefa a cada 60 s, autor `sistema`; desligada na suíte) | P-08 | `NaoComparecimentoAutomaticoTest` |
| Confirmação explícita de manutenção sobre reserva aprovada e lista de manutenções | RF-23, RF-21 | `AdministracaoApiTest` |
| Devoluções em atraso (sinalização periódica e `GET /retiradas/pendentes`) | RF-26 | `AdministracaoApiTest` |
| Consulta de auditoria somente leitura | RF-28, RF-29 | `AdministracaoApiTest` |
| Gestão de usuários (cadastro com perfil e local, inativação com revogação de token) | Escopo do Administrador | `AdministracaoApiTest` |
| OpenAPI 3 com teste de sincronia | RF-39 | `DocumentacaoOpenApiTest` |
| Telas: busca para Responsável e Administrador, catálogo somente leitura, usuários, auditoria, manutenções, pendências, cancelar `EM_USO` | RF-07 a RF-10, RF-14, RF-37 | `FluxoCompletoNavegadorIT` (360 e 1920 px) |
| Autor na auditoria de cadastro de recurso e de manutenção (`CadastroRecursoService.por(usuario)`), antes `null` | RF-28 | `AdministracaoApiTest` |
| Auditoria de retirada e devolução (`RETIRADA`, `DEVOLUCAO_<condição>`) | RF-28 | `AdministracaoApiTest` |
| Encerramento de manutenção (`POST /manutencoes/{id}/encerramento`, botão na lista) | RF-21 | `AdministracaoApiTest`; `DocumentacaoOpenApiTest` (40 operações) |
| Sobreposição material × material na criação | Regra crítica "mesmo recurso não se sobrepõe" | `SobreposicaoMaterialTest` |

**Decisões tomadas nesta etapa** (registradas em [bdd-aprovacao-bloqueio.md](testes/bdd-aprovacao-bloqueio.md)):
P-08 ganhou o complemento automático no fim do horário; P-06 foi fechada pelo texto do RF-14 (cancelar em uso é
permitido); no RF-23, confirmar cancela as reservas `APROVADA` do período, e reserva `EM_USO` continua impedindo.

### 9.3 Riscos residuais e pendências

- **Verificação:** feita localmente (A-24): falta corrigir `SobreposicaoMaterialTest` ("08"/"09"), rodar de novo e
  publicar a branch para o CI e o SonarCloud.
- **Desempenho:** feito (A-23). As repetições usaram o jar guardado da rodada, sem compilar a árvore de trabalho. As
  entregas da 9.2 (inclusive a tarefa periódica de não comparecimento automático) **não foram medidas sob carga**.
- **[DEF-016](defeitos/DEF-016-busca-e-criacao-divergem-na-quantidade-de-material.md), aberto (Média):** a busca de
  material conta unidades livres e a criação trata o material como exclusivo; a busca oferece material que a criação
  recusa com 409. Aguarda decisão entre material exclusivo (recomendado para a entrega) e material por quantidade.
- **E2E das telas novas do Administrador** (usuários, auditoria, manutenções, confirmação de colisão): não escrito; só
  a checagem de layout em 360 e 1920 px. A lógica está coberta pelos testes de API.
- **Mensagens sem acentuação** ("Usuario nao autorizado…"): não corrigidas, porque vários testes conferem trechos dessas
  mensagens; trocar sem rodar a suíte é arriscado.
- Continuam: tokens sem expiração e login sem limite de tentativas; usuário inativado sem reativação nem troca de senha;
  sem tela do Swagger UI (o `openapi.yaml` pode ser aberto no Swagger Editor); dados em memória (ADR-010).

## 10. Verificação final antes do commit (2026-11-12, ~19:46)

As seções anteriores não foram alteradas; esta seção resolve as pendências de verificação da 9.3.

### 10.1 Resultado por meta

| Meta | Alvo | Medido (local, `./mvnw -B verify`) | Situação |
|---|---|---|---|
| Cobertura de linhas | ≥ 80% | 1463/1525 = **95,93%** | Atende |
| Cobertura de ramos | ≥ 70% | 543/634 = **85,65%** | Atende |
| Críticos na RTM | 100% | 32/32 (linhas novas da RTM com execução verde, A-25) | Atende |
| Zero bugs/vulnerabilidades críticas | 0 | 0 defeitos abertos ([DEF-016](defeitos/DEF-016-busca-e-criacao-divergem-na-quantidade-de-material.md) corrigido); SonarCloud **a confirmar no CI do PR** | Atende localmente |

### 10.2 Afirmações

**A-25. A suíte inteira passa com todas as alterações da branch, sem caso pulado.**
- *Evidência:* `./mvnw -B verify` local com Colima, 12/11 ~19:46: **523 unitários e 19 ITs (PostgreSQL com
  Testcontainers, concorrência em 1 e 2 JVMs, E2E em Chromium), 0 falhas, 0 pulados**, `All coverage checks have been met`.
  Uma execução anterior (~19:40) teve 2 falhas em `HistoricoReservaTransacaoIT`, que ainda esperavam os motivos de
  histórico sem acento; corrigidas antes desta.
- *Interpretação:* as entregas da 9.2 e os itens abaixo estão verificados localmente, inclusive contra PostgreSQL real.
- *Limite:* execução local; CI e SonarCloud ficam para o PR. A cobertura de linhas variou de 96,01% (A-24) para 95,93%,
  com código removido (A-27) e código novo no mesmo intervalo; a causa da variação não foi isolada.

**A-26. DEF-016 corrigido pela opção A (material exclusivo no intervalo).**
- *Evidência:* red com 5 falhas (`BuscaMaterialExclusivaTest` e `rf10T2` reescrito), green depois de `buscarMateriais`
  passar a usar a mesma regra de disponibilidade de sala e professor. `SobreposicaoMaterialTest` corrigido (horas sem zero
  à esquerda: o JUnit usa `Integer.decode`, que lê `"08"` como octal inválido), 10/10.
- *Interpretação:* o que a busca oferece, a criação aceita; o defeito visível na demonstração saiu.
- *Limite:* deixa de existir o uso de várias unidades do mesmo material por reservas diferentes no mesmo horário (opção B
  registrada como evolução).

**A-27. Código morto removido e testes fracos substituídos.**
- *Evidência:* saíram `RelatorioService.gerarRelatorioUtilizacao`, `contarRejeicoesPorSobreposicao` e a classe
  `RelatorioUtilizacao` (relatório antigo, sem rota e com regra diferente da rota), `AutenticacaoService.autorizar` (sem
  chamada em produção; a autorização fica em `ReservaService` e `Perfis`) e quatro imports sem uso. `RelatoriosTest`
  passou a testar `gerarUtilizacaoPorPeriodo`, o método da rota: os antigos `rf34T1`/`rf35T1`, que só conferiam `>= 0`,
  viraram testes de valor (minutos por sala e estado; conflitos por entidade, ação e período semiaberto). Os três
  `rf02T*`, que só exercitavam `autorizar`, saíram; o RF-02 segue coberto por `MatrizAutorizacaoApiTest` com token real.
  No JavaScript, nenhuma função sem uso e nenhum id inexistente.
- *Limite:* a busca de código morto foi por referência textual a métodos públicos; getters de objetos de domínio usados
  só por testes foram mantidos como parte do modelo.

**A-28. Mensagens acentuadas, sem perder o nome do campo.**
- *Evidência:* 114 textos (mensagens de erro, títulos de ProblemDetail, motivos de histórico e notificações) passaram a ter acento.
  Identificadores (entidades da auditoria como `Manutencao` e `Usuario`, nomes de campo, enums, SQL e chaves) ficaram
  iguais. Onde a palavra acentuada era o nome do campo, a mensagem passou a citar o campo da API (ex.: "fim deve ser
  posterior ao início (campos 'inicio' e 'fim')"), para manter a regra do DEF-008 que `MatrizAutorizacaoApiTest` e
  `CrudRecursoTest` conferem. Os testes que conferiam os textos antigos (API, domínio, IT com PostgreSQL e E2E) foram
  atualizados. O histórico gravado no PostgreSQL preserva os acentos.
- *Limite:* comentários e mensagens de log continuam sem acento (não aparecem ao usuário).

**A-29. E2E das telas novas do Administrador e do Responsável.**
- *Evidência:* `FluxoCompletoNavegadorIT.telasDoAdministradorEDoResponsavel`: manutenção sobre reserva aprovada pede
  confirmação e cancela a reserva; encerramento libera a sala; cadastro e inativação de usuário; auditoria filtrada com o
  Administrador como autor; Responsável busca sem montar reserva e vê o catálogo somente leitura. O E2E tem 5 testes, todos
  verdes.

### 10.3 O que continua em aberto

- **CI e SonarCloud** do PR desta branch (local verde não substitui).
- Tokens sem expiração e login sem limite de tentativas; usuário inativado sem reativação nem troca de senha.
- Alteração de reserva muda só o horário, não os recursos.
- Sem tela do Swagger UI (o `openapi.yaml` abre no Swagger Editor).
- As entregas desta etapa (inclusive a tarefa periódica) não foram medidas sob carga; a rodada 02 usou o jar de `38bbc04`.
- Dados em memória no modo padrão ([ADR-010](adr/ADR-010-escopo-da-persistencia-postgresql.md)).

### 10.4 Atualização: segurança do login (2026-11-12, ~20:15)

**A-30. Sessão com expiração e bloqueio por tentativas no login.**
- *Evidência:* `TokenService` vence o token após 30 min sem uso ou 8 h desde o login (401 "Sessão expirada");
  `AutenticacaoService` bloqueia por 15 min o e-mail com 5 senhas erradas em 15 min (429), também para e-mail
  inexistente. `SegurancaLoginTest` (13 casos, relógio ajustável: bordas de 29/30/31 min, renovação por uso, 8 h em uso
  contínuo, bloqueio e desbloqueio, janela, maiúsculas) e `LoginBloqueioApiTest` (429 em ProblemDetail). `./mvnw -B
  verify`: **536 unitários e 19 ITs, 0 falhas**; linhas 1507/1569 = **96,05%**, ramos 576/673 = **85,59%**.
- *Interpretação:* sai da lista de riscos a ausência de expiração e de limite contra força bruta (RNF-05).
- *Limite:* tokens e contadores em memória; bloqueio por e-mail, não por IP (um atacante pode bloquear temporariamente
  uma conta conhecida). Os relógios da sessão e do bloqueio são o do sistema, independentes do `Clock` de negócio que os
  testes avançam. A persistência das demais entidades continua fora de escopo (README, "Escopo da persistência").

### 10.5 Atualização: testes fracos e mutação automatizada (2026-11-12, noite)

**A-31. Os 4 testes que a auditoria apontou como fracos foram reescritos.**
- *Evidência:* `rnf05T1` confere o que fica guardado (PBKDF2 com sal, mesma senha gera hashes diferentes);
  `rnf06T1` passa ao `TratadorDeErros` uma exceção com SQL, nome de classe e stack trace e exige 500 genérico sem nenhum
  desses textos; `rnf09T1` percorre criação, aprovação, cancelamento e rejeição por sobreposição e procura órfãos no
  histórico, na auditoria e no catálogo; `rc03T4` injeta falha na gravação (constraint e erro inesperado) e exige que
  nada fique gravado e que o lock seja liberado. Um mutante manual que não libera o lock faz o `rc03T4` falhar por
  timeout.
- *Limite:* `rnf09T1` verifica o modo em memória; no PostgreSQL a integridade do histórico vem da FK e do trigger,
  cobertos por `HistoricoReservaTransacaoIT`.

**A-32. Mutação automatizada (PIT) no domínio: escore de 84,6% para 90,6%.**
- *Evidência:* [testes/mutacao-pit.md](testes/mutacao-pit.md). PIT 1.19.1, perfil `-Pmutacao`, 637 mutantes em
  `com.edualoca.domain`, suíte sem os ITs. Rodada 1: 540 mortos (84,6%, força 86,5%). Rodada 4: **577 mortos (90,6%,
  força 91,4%)**, cobertura de linha das classes mutadas 98%. Os sobreviventes viraram 23 casos em
  `LacunasMutacaoPitTest`, a remoção de uma sobrecarga morta da auditoria e o catálogo usando `listarSalasAtivas`.
  Os 60 restantes estão classificados um a um no documento (37 são remoção de `seedSeNecessario()`).
- *Interpretação:* a cobertura alta (96%) é acompanhada de testes que detectam mudança de comportamento em 9 de cada 10
  mutantes do domínio, o que responde ao aviso do enunciado sobre testes triviais.
- *Limite:* só o domínio foi mutado; os ITs ficaram fora; a equivalência dos sobreviventes é análise do autor. O teste
  `filtroDeCapacidade` entrou depois da rodada 4.

**A-33. Intermitência observada em `ConcorrenciaReservaMultiInstanciaIT` (não explicada).**
- *Evidência:* num `verify` completo de 12/11 (~21:00), o cenário `agendaDoProfessorDisputadaEntreDuasInstancias` falhou
  ao subir a segunda JVM ("Instancia externa terminou (codigo 1)"). Isolado, passou 3 de 3; o `verify` completo seguinte
  passou (573 unitários e 19 ITs, linhas 97,12%, ramos 89,99%). O log da instância que falhou se perdeu, porque todas
  gravavam no mesmo arquivo.
- *Interpretação:* falha de infraestrutura do teste, não de regra: o cenário não chegou a disputar reservas. Hipótese não
  confirmada: corrida em `InstanciaExterna.portaLivre()` (a porta é liberada antes de a JVM filha ocupá-la).
- *Ação:* cada instância passou a ter log próprio (`target/instancia-externa-<timestamp>.log`) e a mensagem de erro traz
  as últimas 25 linhas do log, para que uma nova ocorrência mostre a causa. A corrida de porta não foi corrigida.

## Apêndice A: comandos usados

```bash
# Build local (1ª execução falhou por Docker; 2ª com Colima)
./mvnw -B verify
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw -B verify

# CI
gh run list --limit 8
gh run view 43126611038 --log | grep -E "Tests run:|All coverage|ANALYSIS SUCCESSFUL"
gh run download 43126611038 -n jacoco-report
gh api repos/DaniloDamasio/EduAloca/branches/main/protection
gh pr list --state merged --json number,statusCheckRollup

# Mesmo código no merge e no branch
git rev-parse c55a31d^{tree} a670c68^{tree}

# SonarCloud (API pública, somente leitura)
curl -s "https://sonarcloud.io/api/measures/component?component=DaniloDamasio_EduAloca&branch=main&metricKeys=bugs,vulnerabilities,security_hotspots,code_smells,line_coverage,branch_coverage"
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=DaniloDamasio_EduAloca&branch=main&resolved=false&severities=BLOCKER"

# Integridade dos artefatos de desempenho
shasum -a 256 perf/resultados/rodada-01.jtl perf/reservas.jmx perf/usuarios.csv

# Requisitos críticos
grep -c "| Crítico |" DOCS/REQUISITOS/REQUISITOS.md
```
