# Plano de testes: EduAloca

**Versão:** 13/11/2026, válida para a entrega final. **Responsável:** Danilo Damasio (trabalho individual).

Este é o plano geral e vigente. O [plano-tdd.md](plano-tdd.md), de 27/10, é o inventário de regras usado na fase RED do
TDD e fica como registro histórico. Os resultados de cada execução estão no
[relatório de qualidade](../relatorio-qualidade.md), e o vínculo requisito → teste → evidência está na [RTM](../RTM.md).

## 1. Objetivo

Mostrar, com evidência automatizada e repetível, que o EduAloca cumpre as regras críticas do enunciado e as metas
mensuráveis:

| Meta | Critério | Como é imposta |
|---|---|---|
| Cobertura de linhas | ≥ 80% | regra `BUNDLE` do JaCoCo no `pom.xml`; o `verify` falha abaixo disso |
| Cobertura de ramos | ≥ 70% | idem |
| Requisitos críticos na RTM | 100% dos 32, com teste e execução | revisão da [RTM](../RTM.md) a cada entrega |
| Bugs e vulnerabilidades críticas | 0 | SonarCloud a cada PR e a cada push na `main`; defeitos em [defeitos/](../defeitos/) |

## 2. Escopo

**Dentro:** a API REST (40 operações) e as regras de domínio, a interface web dos três perfis, a persistência nos dois
modos (memória e PostgreSQL), a integração de notificação, a tarefa periódica, segurança (autenticação, autorização,
sessão, bloqueio por tentativas, cabeçalhos) e desempenho da busca e da criação de reserva.

**Fora, com motivo:**

| Item | Motivo |
|---|---|
| Ambiente de produção | Não existe. Toda medição é local ou no CI |
| Pentest, DAST e varredura de dependências (SCA) | Sem ferramenta configurada. A análise estática do SonarCloud cobre só parte do risco |
| Acessibilidade formal (RNF-14) | Sem ferramenta automatizada. O E2E confere só layout de 360 a 1920 px e mensagens de erro |
| Persistência de manutenções, usuários, auditoria, retiradas e sessões | Fora do escopo do banco por decisão ([ADR-010](../adr/ADR-010-escopo-da-persistencia-postgresql.md)); testadas em memória |

## 3. Riscos que orientam a prioridade

Os testes mais fortes vão para o que derruba a nota ou a confiança no sistema:

| Risco | Prioridade | Como é testado |
|---|---|---|
| Dupla reserva sob concorrência (RF-17, RNF-08) | Crítica | 20 pedidos simultâneos contra PostgreSQL real, em 1 e em 2 JVMs (`ConcorrenciaReservaIT`, `ConcorrenciaReservaMultiInstanciaIT`), mais a carga com disputa |
| Sobreposição aceita por algum caminho (RF-15, RF-16) | Crítica | `RegrasCriticasTest`, `SobreposicaoMaterialTest` (bordas do intervalo semiaberto), `ReservaRepositorioJdbcIT` (constraint) |
| Falha de autorização (RF-02, RF-03) | Crítica | `MatrizAutorizacaoApiTest`: perfil × rota, célula a célula, com token do login real |
| Aprovação, manutenção e estados da reserva fora da regra | Alta | BDD `aprovacao-bloqueio.feature`, `MaquinaEstadosReservaTest`, `AdministracaoApiTest` |
| Histórico ou auditoria alteráveis ou incompletos (RF-27 a RF-29) | Alta | `HistoricoAuditavelReservaTest`, `HistoricoReservaTransacaoIT` (trigger append-only) |
| Perda de dados no reinício (modo PostgreSQL) | Alta | `PersistenciaReinicioIT` (duas subidas no mesmo banco), `CatalogoRepositorioJdbcIT` |
| Notificação externa derrubando a operação (RF-33) | Média | WireMock com 404, 500, timeout e corpo inválido |
| Lentidão sob carga (RNF-01 a RNF-03) | Média | JMeter, local e manual |

## 4. Níveis, ferramentas e onde rodam

| Nível | O que cobre | Exemplos | Ferramenta | Fase / onde |
|---|---|---|---|---|
| Unidade e domínio | Regras dos serviços, estados, sobreposição, bordas de tempo com relógio ajustável, segurança do login | `RegrasCriticasTest`, `SegurancaLoginTest`, `NaoComparecimentoAutomaticoTest` | JUnit 5, testes parametrizados, AssertJ | Surefire, local e CI |
| API caixa-preta | Rotas com token real, `ProblemDetail`, validação, matriz de autorização, OpenAPI em sincronia com o código | `MatrizAutorizacaoApiTest`, `AdministracaoApiTest`, `DocumentacaoOpenApiTest` | Spring Boot Test + MockMvc | Surefire, local e CI |
| Aceitação (BDD) | Aprovação, bloqueio por manutenção e transições, em linguagem de negócio | `AprovacaoBloqueioFeatureTest` | Cucumber | Surefire, local e CI |
| Arquitetura | Regras de dependência entre camadas (RNF-15) | `ArquiteturaCamadasTest` | JUnit 5 | Surefire, local e CI |
| Integração externa | Adaptador HTTP de notificação | `IntegracaoNotificacaoExternaTest`, `NotificacaoExternaIntegradaApiTest` | WireMock (porta dinâmica, offline) | Surefire, local e CI |
| Integração com banco | Constraint de exclusão, concorrência, histórico transacional, catálogo, reinício | os 6 `*IT` com PostgreSQL | Testcontainers (`postgres:16-alpine`) | Failsafe, local com Docker e CI |
| Ponta a ponta (E2E) | Fluxo dos três perfis na interface real; layout de 360 a 1920 px | `FluxoCompletoNavegadorIT` | Playwright 1.64.0, Chromium headless | Failsafe, local com navegador e CI |
| Eficácia dos testes | Se os testes detectam código errado | mutação manual das regras críticas; PIT no domínio | PIT 1.19.1 (`-Pmutacao`) | Local e manual, fora do CI ([mutacao-pit.md](mutacao-pit.md), [mutacao-manual.md](mutacao-manual.md)) |
| Desempenho | Busca e criação sob disputa, de 50 a 400 usuários | `perf/reservas.jmx` | JMeter 5.6.3 | Local e manual ([performance/rnf.md](../performance/rnf.md)) |
| Análise estática | Bugs, vulnerabilidades, hotspots, code smells, duplicação | — | SonarCloud | CI |

Tamanho atual da suíte automatizada: 573 testes no Surefire e 24 no Failsafe (7 classes).

## 5. Técnicas de projeto de casos

- **Valor-limite** nas regras de tempo: borda do intervalo semiaberto `[início, fim)` na sobreposição, tolerância de
  15 min do não comparecimento (14, 15 e 16 min), 29, 30 e 31 min de inatividade do token, 5ª tentativa de login.
- **Partição de equivalência** por perfil e por estado da reserva (matriz de autorização e máquina de estados).
- **Tabela de decisão** para aprovação por competência de local e manutenção com e sem confirmação (RF-23).
- **Concorrência real:** threads disparadas juntas contra o banco, contando 201, 409 e linhas gravadas por SQL.
- **TDD** com red e green registrados em commit ([evidencia-tdd.md](evidencia-tdd.md) e a coluna de commit da RTM),
  e cada defeito corrigido com um teste que falhava antes ([defeitos/](../defeitos/)).

## 6. Ambientes e dados

| Ambiente | Detalhe |
|---|---|
| CI (referência) | GitHub Actions `ubuntu-latest`, Temurin 21, Docker nativo e Chromium instalado pelo Playwright |
| Local | JDK 21; Docker para os ITs (com Colima, as variáveis do README); navegador do Playwright para o E2E |

- **Dados:** cada teste começa do seed. O `BancoResetExtension` limpa o estado em memória antes de cada teste. Os ITs
  sobem um PostgreSQL novo por classe.
- **Tempo:** os testes de borda usam `RelogioAjustavel` em vez do relógio do sistema. A tarefa periódica fica desligada
  na suíte e é chamada diretamente pelos testes.
- **Segredos:** nenhum. As senhas são de seed e a chave da notificação externa é falsa, servida pelo WireMock.

## 7. Critérios de entrada e de saída

**Entrada (para começar a testar uma mudança):** o código compila (`./mvnw -B package -DskipTests`), o requisito
afetado está identificado em [REQUISITOS.md](../REQUISITOS/REQUISITOS.md) e, para regra nova, o teste é escrito
antes e falha (red).

**Saída (para fazer merge na `main`):**

1. Check "Build e Testes" do CI verde. A proteção da branch exige isso (`strict`, `enforce_admins`). O check inclui o
   `verify` completo: unitários, ITs com PostgreSQL, E2E e gate JaCoCo (≥ 80% de linhas, ≥ 70% de ramos).
2. Nenhum teste pulado ou desligado sem motivo registrado.
3. Quality gate do SonarCloud OK, sem bug, vulnerabilidade ou hotspot aberto.
4. A RTM atualizada para o requisito afetado e, se havia defeito, o `DEF-xxx` registrado com o teste que o reproduz.

**Saída da entrega:** as quatro metas da seção 1 atendidas no CI da `main`, os relatórios do commit entregue
versionados em [evidencias-ci/](evidencias-ci/) e o resultado consolidado no
[relatório de qualidade](../relatorio-qualidade.md).

**Suspensão:** um teste intermitente não é reexecutado até passar. Ele é investigado e registrado (exemplo: A-33 no
relatório de qualidade, IT de duas JVMs).

## 8. Gestão de defeitos

Cada defeito vira um arquivo `DOCS/defeitos/DEF-xxx-*.md` com severidade (Alta, Média ou Baixa), passos, esperado e
obtido, causa, correção e o teste que falhava antes e passa depois. Prioridade: a severidade Alta é corrigida antes de
qualquer item novo. Situação na entrega: 17 registrados (9 Alta, 5 Média, 3 Baixa), 17 corrigidos.

## 9. Entregáveis de teste

| Entregável | Onde |
|---|---|
| Suíte automatizada | `src/test/` |
| Relatórios Surefire, Failsafe e JaCoCo | artifacts de cada run do CI (90 dias) e cópia versionada do commit entregue em [evidencias-ci/](evidencias-ci/) |
| Análise estática | [SonarCloud](https://sonarcloud.io/project/overview?id=DaniloDamasio_EduAloca) |
| Rastreabilidade | [RTM.md](../RTM.md) |
| Mutação | [mutacao-pit.md](mutacao-pit.md), [mutacao-manual.md](mutacao-manual.md) |
| Carga | [performance/rnf.md](../performance/rnf.md) (plano), [performance/resultado-02.md](../performance/resultado-02.md) (resultado) |
| Defeitos | [defeitos/](../defeitos/) |
| Relatório final | [relatorio-qualidade.md](../relatorio-qualidade.md) |

## 10. Como executar

Comandos e pré-requisitos no README, seção "Executando a Suíte Localmente": suíte completa, sem Docker ou navegador, e
o que fazer quando o navegador não abre.
