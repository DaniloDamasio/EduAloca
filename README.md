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

## Roadmap

- [x] Definição de personas e objetivos
- [x] Requisitos funcionais e não funcionais
- [x] Modelagem de dados
- [x] Jornadas de usuário
- [x] Fase RED/GREEN do TDD (123 testes unitários)
- [x] CI/CD (GitHub Actions: build, testes, relatórios Surefire/Failsafe)
- [ ] Testes de integração, concorrência, e2e
- [ ] Análise estática (SonarCloud, JaCoCo)
- [ ] Teste de carga (JMeter)
- [ ] RTM (Requirements Traceability Matrix)
