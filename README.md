# EduAloca — Sistema de Alocação de Salas, Professores e Materiais

Sistema para organizar a alocação de salas, professores e materiais escolares, evitando
conflitos de horário e dupla reserva de recursos, com três perfis de acesso:
**Solicitante**, **Responsável** e **Administrador**.

## Visão Geral

O EduAloca organiza a reserva de salas, professores e materiais de uma instituição de
ensino. Entre as principais funcionalidades estão: busca de recursos por tipo, capacidade,
localização e disponibilidade; criação, alteração e cancelamento de reservas; aprovação
obrigatória para recursos restritos; bloqueio de recursos em manutenção; controle de
retirada e devolução de materiais; e histórico auditável de todas as mudanças. Trabalho
acadêmico do 4º semestre, com foco em qualidade, segurança, rastreabilidade e desempenho.

## Personas

| Persona | Descrição | Documento |
|---|---|---|
| Solicitante | Professor ou coordenador que consulta disponibilidade e cria, altera ou cancela suas próprias reservas | [DOCS/PERSONAS/solicitante.md](DOCS/PERSONAS/solicitante.md) |
| Responsável | Aprova solicitações de recursos restritos, valida alocação de docentes e acompanha retirada/devolução de material | [DOCS/PERSONAS/responsavel.md](DOCS/PERSONAS/responsavel.md) |
| Administrador | Gerencia salas, professores, materiais, usuários, bloqueios e períodos de manutenção | [DOCS/PERSONAS/administrador.md](DOCS/PERSONAS/administrador.md) |

## Objetivos

Os objetivos gerais, específicos e critérios de sucesso mensuráveis do projeto estão em
[DOCS/REQUISITOS/OBJETIVOS.md](DOCS/REQUISITOS/OBJETIVOS.md).

## Requisitos Funcionais e Não Funcionais

Os requisitos funcionais (RF-01 a RF-39) e não funcionais (RNF-01 a RNF-16) estão
documentados em [DOCS/REQUISITOS/REQUISITOS.md](DOCS/REQUISITOS/REQUISITOS.md), em tabelas
com origem (requisito do enunciado + persona), perfil, prioridade e critério de aceite
mensurável.

## Jornadas de Usuário

As principais jornadas (caminho feliz, aprovação de recurso restrito, conflito de
horário, dupla reserva concorrente, manutenção, retirada/devolução, auditoria, relatórios
e ciclo de estados da reserva) estão documentadas em
[DOCS/REQUISITOS/JORNADAS.md](DOCS/REQUISITOS/JORNADAS.md).

## Modelagem de Dados

O modelo relacional está em
[DOCS/REQUISITOS/MODELAGEM_DADOS.dbml](DOCS/REQUISITOS/MODELAGEM_DADOS.dbml), no formato
DBML. Para visualizar o diagrama, importe o arquivo em
[dbdiagram.io](https://dbdiagram.io/d). A descrição textual das entidades e decisões de
modelagem está em
[DOCS/REQUISITOS/MODELAGEM_DADOS.md](DOCS/REQUISITOS/MODELAGEM_DADOS.md).

**Entidades:** usuario, professor, competencia, professor_competencia, sala, material,
reserva, reserva_recurso, manutencao, retirada_devolucao, auditoria_evento,
notificacao_enviada

## Documento de Escopo

O enunciado do projeto, tratado como fonte única de verdade sobre escopo, requisitos
obrigatórios e regras críticas, está em [DOCS/enunciado.md](DOCS/enunciado.md).

## Estrutura do Projeto

```
EduAloca/
├── README.md
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/edualoca/
│   │   └── resources/
│   └── test/
│       └── java/com/edualoca/
└── DOCS/
    ├── enunciado.md
    ├── PERSONAS/
    │   ├── solicitante.md
    │   ├── responsavel.md
    │   └── administrador.md
    └── REQUISITOS/
        ├── OBJETIVOS.md
        ├── REQUISITOS.md
        ├── JORNADAS.md
        ├── MODELAGEM_DADOS.md
        └── MODELAGEM_DADOS.dbml
```

## Stack

- Java 21, Spring Boot 4.1.1, Maven
- Arquitetura MVC
- PostgreSQL — ver justificativa em [DOCS/arquitetura.md](DOCS/arquitetura.md#11-decisão-de-banco-de-dados-postgresql)

## Integração Contínua

Pipeline em [.github/workflows/ci.yml](.github/workflows/ci.yml), disparado em `pull_request`
e em `push` na `main`: Java 21 (Temurin), `./mvnw -B verify` e publicação dos relatórios de
Surefire e Failsafe como artifact. O check `Build e Testes` é obrigatório na branch `main`
(configurado em Settings → Branches → Branch protection rules).

Runs de validação do pipeline (PR [#4](https://github.com/DaniloDamasio/EduAloca/pull/4)):

| Run | Resultado | Descrição |
|---|---|---|
| [40528622858](https://github.com/DaniloDamasio/EduAloca/actions/runs/40528622858) | ✅ verde | Primeira execução do pipeline, 123 testes passando |
| [40528813431](https://github.com/DaniloDamasio/EduAloca/actions/runs/40528813431) | ❌ vermelho | Asserção quebrada de propósito para validar falha do CI |
| [40528932207](https://github.com/DaniloDamasio/EduAloca/actions/runs/40528932207) | ✅ verde | Após reverter a quebra, pipeline volta a passar |

## Executando a Suíte Localmente

```
./mvnw -B verify
```

Isso roda toda a suíte (unitária e de integração) e o gate de cobertura do JaCoCo
(≥80% linha, ≥70% branch). Os testes de integração com o provedor de notificação externa
([IntegracaoNotificacaoExternaTest](src/test/java/com/edualoca/IntegracaoNotificacaoExternaTest.java))
sobem um servidor WireMock em porta dinâmica — não dependem de rede externa nem de
nenhuma credencial real, e rodam de forma determinística em qualquer máquina.

## Especificação executável (BDD com Cucumber)

Os cenários de API de [DOCS/testes/bdd-aprovacao-bloqueio.md](DOCS/testes/bdd-aprovacao-bloqueio.md)
rodam como features Gherkin em português, com Cucumber-JVM na JUnit Platform:

- Feature: [src/test/resources/features/aprovacao-bloqueio.feature](src/test/resources/features/aprovacao-bloqueio.feature)
  (mesmo texto do documento; uma `Regra` por funcionalidade).
- Steps: [AprovacaoBloqueioSteps](src/test/java/com/edualoca/bdd/AprovacaoBloqueioSteps.java). Cada
  ação chama a API por MockMvc com o token Bearer do perfil que a executa.
- Runner: [AprovacaoBloqueioFeatureTest](src/test/java/com/edualoca/bdd/AprovacaoBloqueioFeatureTest.java),
  executado pelo Surefire em `./mvnw -B verify`, portanto no CI e dentro do gate do JaCoCo.
- Linhas de exemplo que dependem de pergunta em aberto (P-xx) ficam na feature com `@bloqueado`
  e aparecem como *skipped* (`cucumber.filter.tags` em `src/test/resources/junit-platform.properties`).

Para rodar só as features: `./mvnw -B test -Dtest=AprovacaoBloqueioFeatureTest`.

## Nota de segurança da API HTTP

Camada HTTP em `com.edualoca.api` (reservas, manutenção, disponibilidade de salas).

- **Autenticação:** toda rota exige `Authorization: Bearer <token>`. O token é opaco, gerado
  com `SecureRandom` (256 bits) e resolvido no servidor (`TokenService`). Sem token ou com
  token desconhecido, a resposta é 401. O token nunca vai na URL.
- **Autorização (RF-02, RF-03, RF-19):** o ator é sempre o usuário do token, nunca um campo
  do corpo. Perfil e dono são checados no domínio (`ReservaService`). `POST /manutencoes`
  é exclusivo do Administrador.
- **Entrada (RNF-04):** JSON malformado, data fora do formato ISO, tipo/id de recurso
  inexistente, quantidade < 1 ou mais de 50 recursos por reserva resultam em 400.
- **Erros (RF-38, RNF-06):** corpo único `{"mensagem": ...}`, sem stacktrace, nome de classe
  ou mensagem de framework. Erro inesperado vira 500 genérico; o detalhe fica só no log.
- **Limitações conhecidas (decisão pendente):** ainda não há endpoint de login que emita
  token a partir de credencial (RF-01). Os tokens ficam em memória, sem expiração nem
  revogação, e não há rate limit. Isso vale para esta fase acadêmica sem persistência e
  deve ser resolvido antes de expor a API (ex.: Spring Security com expiração e throttling
  no login). A auditoria do cadastro de manutenção registra autor `null` (RF-28): é um
  comportamento herdado de `CadastroRecursoService`.

## Roadmap

- [x] Definição de personas e objetivos
- [x] Requisitos funcionais e não funcionais
- [x] Modelagem de dados
- [x] Jornadas de usuário
- [x] Fase RED/GREEN do TDD (123 testes unitários)
- [x] CI/CD (GitHub Actions: build, testes, relatórios Surefire/Failsafe)
- [x] Análise estática (SonarCloud, JaCoCo)
- [x] Adapter HTTP de notificação externa isolado via WireMock (RF-31/32/33, RNF-16)
- [x] BDD executável com Cucumber-JVM sobre a API (MockMvc + token por perfil)
- [ ] Testes de integração com persistência real, concorrência, e2e
- [ ] Teste de carga (JMeter)
- [x] RTM (Requirements Traceability Matrix) — cobertura inicial, ver [DOCS/RTM.md](DOCS/RTM.md)
