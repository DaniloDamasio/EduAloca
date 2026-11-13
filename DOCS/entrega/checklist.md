# Checklist de entrega: avaliação externa

- **Data:** 13/11/2026
- **Commit avaliado:** `c4098f2` (`main`, merge do PR #18), clonado por HTTPS anônimo em diretório temporário
- **Ambiente:** macOS (notebook corporativo), JDK Temurin 21.0.9, Docker 29.5.2 via Colima. `HOME` e `user.home`
  apontando para um diretório vazio: repositório Maven, wrapper, cache do Playwright e configuração do Testcontainers
  começaram do zero (593 artefatos baixados do Maven Central).
- **Regra desta etapa:** só avaliação. Nada foi corrigido.

## 1. Execução seguindo apenas o README

| # | Passo do README | Resultado | Observação |
|---|---|---|---|
| 1 | `./mvnw -B package -DskipTests` | ✅ OK (34 s) | Jar gerado |
| 2 | `java -jar target/edualoca-0.0.1-SNAPSHOT.jar` (modo em memória) | ✅ OK | Sobe em 0,6 s. `/` e `/openapi.yaml` dão 200; as 5 contas de demonstração fazem login; sem token, 401; Solicitante no relatório, 403 |
| 3 | `docker run … postgres:16-alpine` + `java -jar … --edualoca.persistencia.*` | ✅ OK | Esquema criado (7 tabelas). Sala e reserva criadas, sobreposição dá 409, e as duas sobrevivem ao reinício |
| 4 | `./mvnw -B verify` (comando literal) | ❌ BUILD FAILURE | 573 unitários verdes. As 7 classes do Failsafe com erro: 6 com "Could not find a valid Docker environment" (o Testcontainers não acha o Colima sozinho, como o README avisa) e o E2E com o Chromium morto por **SIGKILL** |
| 5 | `DOCKER_HOST=… TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=… ./mvnw -B verify` (variante Colima) | ❌ BUILD FAILURE | Os 6 ITs com PostgreSQL passam. Falha só `FluxoCompletoNavegadorIT.abrirNavegador` (`TargetClosedError`, SIGKILL). Sem relatório JaCoCo |
| 6 | `./mvnw -B verify -Dit.test='!FluxoCompletoNavegadorIT'` (+ variáveis do Colima) | ✅ BUILD SUCCESS | Gate atendido: linhas 1666/1718 = **96,97%**, ramos 634/709 = **89,42%**. ⚠️ O Failsafe rodou **592** testes: o filtro negado substitui o padrão `*IT` e repete as 50 classes unitárias |
| 7 | `./mvnw -B verify -DskipITs` | ✅ BUILD SUCCESS | 573 testes, gate atendido. ⚠️ O README diz "96,9% de linhas e 89,4% de ramos"; o medido é **85,04%** (1461/1718) e **83,64%** (593/709) |

**Falha atribuída ao ambiente (esperada):** o SIGKILL do Chromium baixado num cache novo é a política de segurança do
notebook corporativo, como descrito no README ("Se o navegador não abrir"). A mesma suíte passa inteira no CI da `main`
para este commit (run [43354876270](https://github.com/DaniloDamasio/EduAloca/actions/runs/43354876270): verify,
SonarCloud e upload dos artifacts com sucesso). Por isso não está entre os bloqueadores.

**O que faltou ou falhou no README:**

1. O comando principal (`./mvnw -B verify`) não passa em macOS com Colima sem as variáveis. Está documentado, mas fica
   na seção de baixo e não no passo principal.
2. O número de cobertura da variante `-DskipITs` está errado (passo 7).
3. A variante `-Dit.test='!FluxoCompletoNavegadorIT'` repete os unitários no Failsafe (passo 6). O resultado vale, mas a
   contagem de testes de integração do console não confere com a documentada (19 + 5).
4. O link "Documento de Escopo" (`DOCS/enunciado.md`) não abre no clone, porque o arquivo está no `.gitignore`.
5. `DOCS/api.md` não informa os valores aceitos para `tipo` em `POST /salas`: um corpo montado só pelo api.md levou
   400 ("Tipo da sala é obrigatório"). O valor só aparece no exemplo do `openapi.yaml` (`laboratorio`).

## 2. Entregáveis da especificação

Legenda de "Atualizado": **Sim** = coerente com o commit `c4098f2`; **Parcial** = existe e é usável, com divergência
apontada; **Não** = desatualizado ou ausente.

| # | Item | Existe | Atualizado | Caminho | Observação |
|---|---|---|---|---|---|
| 1 | Repositório com histórico de trabalho | Sim | Sim | <https://github.com/DaniloDamasio/EduAloca> | 18 PRs mergeados (19/10 a 13/11), uma branch por entrega |
| 1b | Revisão por pares | **Não** | — | PRs #1 a #18 | **0 revisões** nos 18 PRs. Os comentários são do `sonarqubecloud` (14), do `github-advanced-security` (1) e do próprio autor (1). O README ("Autoria e revisão") justifica: trabalho individual, revisão substituída por CI obrigatório, mutação e relatório |
| 2 | Aplicação executável e instruções de instalação | Sim | Sim | [README.md](../../README.md), seção "Executando a Aplicação" | Passos 1 a 3 da seção 1 funcionaram sem ajuste |
| 3 | README | Sim | Parcial | [README.md](../../README.md) | Cobertura do `-DskipITs` errada; link para o `enunciado.md` quebrado |
| 3b | Documentação da API | Sim | Sim | [DOCS/api.md](../api.md), [openapi.yaml](../../src/main/resources/static/openapi.yaml) | 40 rotas no api.md = 40 operações no OpenAPI; `DocumentacaoOpenApiTest` mantém o OpenAPI em sincronia com o código. Lacuna: valores de `tipo` |
| 4 | RTM (requisito → risco → teste → evidência) | Sim | Parcial | [DOCS/RTM.md](../RTM.md) | 32/32 críticos. A evidência citada é o CI do `6e97d9d` (PR #17); depois dele, `AgendamentoConfig.java` mudou (30 s, `60da957`). O nome real é `DOCS/RTM.md`: um `docs/rtm.md` não existe num sistema que diferencia maiúsculas |
| 5a | Diagrama de contexto | Sim | Sim | [DOCS/arquitetura.md §14](../arquitetura.md) | Mermaid `C4Context` |
| 5b | Diagrama de componentes (e contêineres) | Sim | Sim | [DOCS/arquitetura.md §15–16](../arquitetura.md) | `C4Container` e `C4Component`; refletem o código (tarefa a cada 30 s, 40 operações) |
| 5c | Sequências críticas | Sim | Sim | [DOCS/arquitetura.md §17](../arquitetura.md) | 3 `sequenceDiagram`: reserva sob concorrência, tarefa periódica, relatório |
| 6a | Plano de testes | Sim | Sim | [DOCS/testes/plano-testes.md](../testes/plano-testes.md) | Versão de 13/11, vigente; [plano-tdd.md](../testes/plano-tdd.md) como histórico |
| 6b | Suíte automatizada | Sim | Sim | `src/test/` | 573 unitários + 24 de integração/E2E (19 executados aqui; o E2E foi barrado pelo ambiente) |
| 6c | Relatório de testes | Sim | Parcial | [DOCS/testes/evidencias-ci/README.md](../testes/evidencias-ci/README.md), [relatorio-qualidade.md §10](../relatorio-qualidade.md) | Não há documento próprio com esse nome. O resultado está no resumo de evidências do CI (run do `aa5e48d`, mesmo código do `c4098f2`) e no relatório de qualidade |
| 7 | Pipeline de CI | Sim | Sim | [.github/workflows/ci.yml](../../.github/workflows/ci.yml) | Em `pull_request` e `push` na `main`. Verde no `c4098f2`. Check "Build e Testes" obrigatório na `main`. O passo do SonarCloud é pulado sem aviso se faltar `SONAR_TOKEN` |
| 8a | Relatório do SonarCloud | Sim | Sim | [SonarCloud](https://sonarcloud.io/project/overview?id=DaniloDamasio_EduAloca) | Última análise no `c4098f2`: gate **OK**, 0 bugs, 0 vulnerabilidades, 0 code smells, 0 hotspots pendentes, 0 issues BLOCKER/CRITICAL, 0% de duplicação. Público; não há cópia versionada (só o link) |
| 8b | Relatório do JaCoCo | Sim | Sim | [DOCS/testes/evidencias-ci/jacoco/index.html](../testes/evidencias-ci/jacoco/index.html) | 96,97% de linhas, 89,28% de ramos (CI). Reproduzido aqui: 96,97% / 89,42% sem o E2E. Artifacts do CI válidos até 11/02/2027 |
| 9a | Plano do teste de carga | Sim | Sim | [DOCS/performance/rnf.md](../performance/rnf.md), [perf/reservas.jmx](../../perf/reservas.jmx), [perf/executar-rodada-02.sh](../../perf/executar-rodada-02.sh) | Vigente (13/11) |
| 9b | Resultado do teste de carga | Sim | Parcial | [DOCS/performance/resultado-02.md](../performance/resultado-02.md) | Mediu o jar do `38bbc04`. Depois dele, 36 arquivos de `src/main` mudaram (persistência do catálogo, sessão e bloqueio de login, agendador). O próprio relatório de qualidade declara isso como limite. Os `.jtl` brutos estão numa release **em rascunho**, que dá 404 para quem é de fora. O texto diz que os brutos não são versionados, mas `app.log`, `gc.log`, `jmeter.log` etc. estão no git (só o `.jtl` fica fora) |
| 10 | Registro de defeitos (encontrados, priorizados, corrigidos) | Sim | Sim | [DOCS/defeitos/](../defeitos/) | DEF-001 a DEF-017, todos com severidade e status "Corrigido" com reteste. Não há índice consolidado na pasta. DEF-002 a DEF-010 dizem "commit desta correção" em vez do hash |
| 11 | Relatório final de qualidade | Sim | Sim | [DOCS/relatorio-qualidade.md](../relatorio-qualidade.md) | Resumo executivo de 13/11 com as 4 metas atendidas e a recomendação. As seções 1 a 7 são históricas (commit `a670c68`), avisadas no topo |
| 12 | Apresentação e demonstração | Sim | Sim | [DOCS/apresentacao.md](../apresentacao.md), [DOCS/apresentacao/slides.html](../apresentacao/slides.html) | O PDF não é versionado (gerado do HTML) |
| — | Enunciado (escopo) | **Não** no repo | — | `DOCS/enunciado.md` (no `.gitignore`) | 13 links em 11 arquivos apontam para ele. Dois (`REQUISITOS/*.md` → `enunciado.md`) têm caminho relativo errado mesmo com o arquivo presente |

## 3. Bloqueadores, em ordem

1. **Não há revisão por pares (entregável 1).** São 0 revisões em 18 PRs. A substituição está justificada no README e no
   relatório, mas o critério literal do enunciado não foi cumprido. Cabe ao professor aceitar ou não a justificativa.
2. **O enunciado não está versionado.** O README e mais 10 documentos o citam como "fonte única de verdade", e quem
   clona não consegue abrir. Sem ele, o avaliador não confere escopo nem metas a partir do repositório.
3. **Os dados brutos da carga estão inacessíveis.** A release `carga-rodada-02` está em rascunho (404 anônimo), e é ela
   que `resultado-02.md` cita, com SHA-256, como prova dos números.
4. **A cobertura documentada para `-DskipITs` está errada no README.** Diz 96,9% / 89,4%; o medido é 85,0% / 83,6%. É um
   número de evidência incorreto num relatório que afirma "nenhum número foi estimado".
5. **A carga não mediu a versão entregue.** Foi medido o `38bbc04`, com 36 arquivos de `src/main` mudados depois,
   inclusive o caminho de escrita no PostgreSQL. O limite está declarado, então não invalida a entrega, mas é o ponto
   mais fraco da evidência de desempenho.

**Pendências menores** (não bloqueiam): a RTM cita o CI do `6e97d9d` em vez do commit final; o comando sem E2E repete os
unitários no Failsafe; falta um documento próprio de relatório de testes; `resultado-02.md` se contradiz sobre os
brutos versionados; DEF-002 a DEF-010 não trazem o hash da correção; o api.md não informa os valores de `tipo`.

## 4. Situação dos bloqueadores na `v1.0.0-rc1`

As seções 1 a 3 são o retrato da avaliação do `c4098f2` e não foram reescritas.

| # | Bloqueador | Situação | Como |
|---|---|---|---|
| 1 | Revisão por pares | Limite declarado | Sem correção técnica possível num trabalho individual. A justificativa está no README ("Autoria e revisão") e no [CHANGELOG](../../CHANGELOG.md) |
| 2 | Enunciado não versionado | Corrigido | `DOCS/enunciado.md` saiu do `.gitignore` e entrou no repositório. Os links relativos de `DOCS/REQUISITOS/*.md` foram corrigidos |
| 3 | Brutos da carga inacessíveis | Corrigido (depois da tag) | Os SHA-256 dos dois arquivos conferem com o `resultado-02.md`. A release [carga-rodada-02](https://github.com/DaniloDamasio/EduAloca/releases/tag/carga-rodada-02) foi publicada em 13/11/2026, depois da `v1.0.0-rc1`. Como a release não faz parte do conteúdo da tag, não foi preciso criar a `rc2` |
| 4 | Cobertura do `-DskipITs` errada no README | Corrigido | Números medidos (85,04% e 83,64%). O comando sem E2E passou a ser `-Dit.test='*IT,!FluxoCompletoNavegadorIT'` (19 ITs, sem repetir os unitários) |
| 5 | Carga não mediu a versão entregue | Limite declarado | Declarado no CHANGELOG, no relatório de qualidade e no índice de evidências |
