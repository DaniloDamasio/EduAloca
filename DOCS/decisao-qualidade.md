# Decisão de qualidade: EduAloca `v1.0.0-rc1`

- **Versão avaliada:** tag [`v1.0.0-rc1`](https://github.com/DaniloDamasio/EduAloca/tree/v1.0.0-rc1), commit
  [`428f282`](https://github.com/DaniloDamasio/EduAloca/commit/428f282f96d8d05f4c09929f84fa8ac921b27c2d)
- **Data:** 13/11/2026
- **Fonte:** o [pacote de evidências](evidencias/README.md). Todo link abaixo abre o documento na própria tag ou aponta
  para o CI e o SonarCloud desse commit.
- **Relator e responsável pela decisão:** Danilo Damasio (único integrante)

## 1. Decisão

**LIBERAR COM RESSALVAS** para **entrega acadêmica, avaliação e demonstração**.

**Não liberado para produção.** A evidência que existe é a suíte automatizada em ambiente efêmero (CI no
`ubuntu-latest` e Testcontainers), um teste de carga feito num único notebook, numa versão anterior, e a execução local.
Não houve homologação, implantação, operação com dados reais, revisão por outra pessoa nem teste de carga do commit
avaliado. Parte do estado (usuários, manutenções, auditoria de cadastro, retiradas e sessões) fica só em memória e se
perde num reinício.

**Por que não "liberar" sem ressalvas:** a carga não mediu este commit, o E2E só foi reproduzido no CI e há riscos
aceitos que afetam um uso real (seção 3).

**Por que não "bloquear":** as quatro metas mensuráveis do enunciado foram atendidas com evidência no commit da tag,
as regras críticas têm teste automatizado contra PostgreSQL real, e nenhum defeito está aberto. Nenhuma ressalva
impede avaliar nem demonstrar o sistema.

## 2. Critérios e evidência

| # | Critério | Alvo | Resultado no `428f282` | Atende | Evidência |
|---|---|---|---|---|---|
| C1 | Cobertura de linhas | ≥ 80% | **97,03%** (1667/1718) | Sim | Artifact `jacoco-report` do [run 43359823197](https://github.com/DaniloDamasio/EduAloca/actions/runs/43359823197); cópia em [evidencias/v1.0.0-rc1/jacoco/](evidencias/v1.0.0-rc1/jacoco/index.html) |
| C2 | Cobertura de ramos | ≥ 70% | **89,42%** (634/709) | Sim | Idem |
| C3 | Requisitos críticos na RTM | 100% | **32 de 32**, com risco, teste e evidência | Sim | [RTM.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/RTM.md) |
| C4 | Bugs e vulnerabilidades críticas | 0 | **0 bugs, 0 vulnerabilidades**, 0 hotspots e 0 code smells. Quality Gate **OK** | Sim | [Quality Gate da análise `8ed949be`](https://sonarcloud.io/api/qualitygates/project_status?analysisId=8ed949be-db1b-4622-acb7-57ab070ee36e) (revisão `428f282`) |
| C5 | Suíte automatizada verde no commit avaliado | 0 falhas | **573 unitários + 24 de integração e E2E**, 0 falhas, 0 pulados | Sim | [Run 43359823197](https://github.com/DaniloDamasio/EduAloca/actions/runs/43359823197) (push na `main`); cópia em [evidencias/v1.0.0-rc1/](evidencias/v1.0.0-rc1/) |
| C6 | Regras críticas (término > início, sem sobreposição, uma reserva aceita em pedidos simultâneos, manutenção bloqueia) | Teste automatizado | Constraint de exclusão no PostgreSQL: 20 pedidos simultâneos em 1 e 2 JVMs resultam em 1 aceito. Sob carga, 0 duplas em 6.308 janelas disputadas | Sim | `ConcorrenciaReservaIT`, `ConcorrenciaReservaMultiInstanciaIT`; [relatório de qualidade](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/relatorio-qualidade.md), resumo executivo |
| C7 | Estados e auditoria (ciclo de estados, só Responsável aprova restrito, iniciada não se apaga, toda mudança auditada) | Teste automatizado | Histórico gravado na mesma transação, com trigger que recusa `UPDATE`/`DELETE`. Matriz de autorização célula a célula com token real | Sim | [RTM.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/RTM.md); `HistoricoReservaTransacaoIT`, `MatrizAutorizacaoApiTest` |
| C8 | Defeitos encontrados, priorizados e corrigidos | Nenhum aberto | **17 registrados** (9 Alta, 5 Média, 3 Baixa) e **17 corrigidos**, cada um com teste que falhava antes da correção | Sim | [DOCS/defeitos/](https://github.com/DaniloDamasio/EduAloca/tree/v1.0.0-rc1/DOCS/defeitos) |
| C9 | Testes não triviais | Evidência além da cobertura | Mutação PIT no domínio: 577 de 637 mutantes mortos (90,6%), mais mutação manual das regras críticas | Sim, com limite (R8) | [relatório de qualidade §10.5](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/relatorio-qualidade.md) |
| C10 | Desempenho sob carga (RNF-01 a RNF-03) | p95 ≤ 2 s na consulta, ≤ 3 s na escrita, 0 erros 5xx | Até 400 usuários: p95 de 1–3 ms na busca e 8–12 ms na criação, 0 erros | **Parcial** (R1, R2) | [resultado-02.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/performance/resultado-02.md) (vigente); [resultado-01.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/performance/resultado-01.md) (histórico); brutos na release [carga-rodada-02](https://github.com/DaniloDamasio/EduAloca/releases/tag/carga-rodada-02) |
| C11 | Aplicação executável a partir do README | Sobe e responde | Num clone limpo: o jar sobe em memória e com PostgreSQL, e os dados sobrevivem ao reinício. A suíte sem o E2E passou (573 + 19, gate atendido) | Sim | [checklist de entrega](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/entrega/checklist.md) |
| C12 | CI em pull requests com análise estática | Obrigatório na `main` | GitHub Actions em todo PR e push na `main`, com o check "Build e Testes" obrigatório e SonarCloud | Sim | [.github/workflows/ci.yml](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/.github/workflows/ci.yml) |
| C13 | Revisão por pares | Revisão de outra pessoa | **Nenhuma**: trabalho individual | **Não** (R9) | README, "Autoria e revisão" |

## 3. Riscos aceitos

Responsável por todos: **Danilo Damasio**, único integrante do projeto.

| # | Risco | Impacto | Justificativa para aceitar nesta versão |
|---|---|---|---|
| R1 | **A carga não mediu o commit avaliado.** A rodada 02 usou o jar do `38bbc04`. Persistência do catálogo, segurança do login e tarefa periódica vieram depois | O desempenho do `428f282` sob carga é desconhecido. Uma regressão no caminho de escrita não seria percebida | A busca e a criação de reserva medidas não mudaram de forma relevante segundo o relatório de qualidade, mas isso não foi medido de novo. Uma nova rodada leva cerca de 1 h e não muda a decisão para o escopo acadêmico |
| R2 | **A carga rodou num único notebook**, com gerador, aplicação e banco na mesma máquina, sem rede e com o banco vazio a cada cenário | Os números não valem para outro hardware, para rede real nem para volume de dados acumulado | Foi a única infraestrutura disponível. O escopo da conclusão está declarado no `resultado-02.md` |
| R3 | **Estado em memória:** manutenções, usuários criados pelo Administrador, auditoria de cadastro, retiradas, notificações e sessões se perdem num reinício. Com PostgreSQL, uma manutenção anterior ao reinício deixa de bloquear o recurso | Um reinício apaga trilha de auditoria de cadastro e pode liberar um recurso que estava em manutenção | Decisão do [ADR-010](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/adr/ADR-010-escopo-da-persistencia-postgresql.md): o enunciado exige ambiente real para a persistência crítica (reservas, dupla reserva, histórico), e essa está em PostgreSQL. **Impede uso em produção** |
| R4 | **Instância única:** no modo em memória, a proteção contra dupla reserva vale dentro de uma JVM. Com várias instâncias, os dados em memória divergem | Implantar o modo em memória com mais de uma instância reabriria o DEF-001 | Com PostgreSQL, a constraint de exclusão protege entre instâncias, e isso é testado com duas JVMs. A implantação prevista é de instância única ([ADR-004](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/REVISAO_ATAM.md#adr-004--instância-única-de-backend-sem-halock-distribuído)) |
| R5 | **Senhas das contas de demonstração fixas no código** do seed | Quem lê o repositório público entra com qualquer perfil numa instância com o seed | As senhas são para demonstração e teste e estão declaradas no README. Quanto às senhas guardadas, o armazenamento usa PBKDF2-SHA256 com sal (DEF-013). **Impede uso em produção** sem trocar o seed |
| R6 | **E2E reproduzido só no CI.** Na máquina do autor, a política de segurança corporativa encerra o Chromium baixado | Um avaliador numa máquina com a mesma restrição não reproduz o E2E localmente | O E2E passou no CI do commit avaliado (5 casos). O README documenta o sintoma e a variante sem o E2E |
| R7 | **Intermitência não explicada** em `ConcorrenciaReservaMultiInstanciaIT`: uma falha ao subir a segunda JVM, em 12/11 (A-33) | Um CI vermelho esporádico, sem relação com regra de negócio | Isolado, passou 3 de 3, e todos os CIs seguintes passaram. A hipótese (corrida na escolha de porta) não foi confirmada |
| R8 | **Mutação PIT medida antes da persistência do catálogo** e só no pacote `domain` | A eficácia dos testes de `persistencia` e `api` não tem medida por mutação | As regras críticas estão no domínio. A persistência é coberta por ITs contra PostgreSQL real |
| R9 | **Sem revisão por pares**: 0 revisões nos 21 PRs | Legibilidade e decisões de projeto não foram vistas por outra pessoa | Trabalho individual. Foi substituída por CI obrigatório, mutação manual e automatizada e este relatório, que não substituem o olhar de outra pessoa |
| R10 | **Sem chave estrangeira** em `reserva_recurso.recurso_id` | A integridade entre reserva e recurso (RNF-09) não é garantida pelo banco | O catálogo e as reservas são gravados pela aplicação. Registrado no ADR-010 |

**Antes de qualquer uso em produção:** persistir tudo o que hoje fica em memória (R3), remover as senhas de seed (R5),
medir a carga no commit candidato num ambiente parecido com o de produção (R1, R2), fazer revisão por outra pessoa
(R9) e homologar com usuários reais.

## 4. Integrantes e declaração

| Integrante | O que fez |
|---|---|
| **Danilo Damasio** (GitHub `DaniloDamasio`) | Único integrante e autor dos 179 commits e dos 21 PRs do repositório (08/10 a 13/11/2026). Fez requisitos, personas, jornadas e modelagem de dados; a arquitetura (C4, sequências, ADRs) e a revisão ATAM; a implementação da API, do domínio, da persistência em PostgreSQL e da interface web; o TDD, os testes unitários, de API, BDD, de integração com Testcontainers e WireMock, e o E2E; o pipeline de CI com JaCoCo e SonarCloud; o teste de carga com JMeter; a mutação manual e com PIT; o registro e a correção dos 17 defeitos; a RTM, o relatório de qualidade, o release `v1.0.0-rc1` e esta decisão |

Declaro que as informações acima correspondem ao trabalho realizado e às evidências do repositório na tag
`v1.0.0-rc1`.

**Danilo Damasio**, 13/11/2026

## 5. Texto para o formulário de entrega do Hub

> **EduAloca: sistema de organização de recursos (salas, professores e materiais)**
> Repositório público: https://github.com/DaniloDamasio/EduAloca
> Versão avaliada: tag `v1.0.0-rc1` (commit `428f282`): https://github.com/DaniloDamasio/EduAloca/tree/v1.0.0-rc1
> Decisão de qualidade: liberar com ressalvas, para avaliação e demonstração, não para produção
> (`DOCS/decisao-qualidade.md`). Evidências em `DOCS/evidencias/README.md`.
> Trabalho individual: Danilo Damasio.
