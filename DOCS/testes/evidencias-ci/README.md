# Evidência de CI do commit entregue

Relatórios do run [43353596650](https://github.com/DaniloDamasio/EduAloca/actions/runs/43353596650) do GitHub Actions (13/11/2026, 02:38 UTC), no PR #18, com o código que entra na `main` (head `aa5e48d`). Os artifacts do CI expiram, então esta cópia fica versionada. Depois desse head, o PR só recebeu documentação: nenhum arquivo de `src/` ou do `pom.xml` mudou.

## Resumo

| Item | Resultado |
|---|---|
| Testes unitários (Surefire) | **573** pelo resumo do Maven, 0 falhas, 0 erros, 0 pulados (os 50 arquivos `TEST-*.xml` somam 569 casos; como no relatório de qualidade, vale o total do Maven, e a suíte Cucumber aparece com contagens diferentes no console e no XML) |
| Testes de integração e E2E (Failsafe) | **24** em 7 classes, 0 falhas, 0 erros, 0 pulados |
| Cobertura de linhas (meta ≥ 80%) | **96,97%** (1666/1718) |
| Cobertura de ramos (meta ≥ 70%) | **89,28%** (633/709) |

A cobertura soma unitários e integração: o relatório roda na fase `verify`, depois do Failsafe, sobre o mesmo `jacoco.exec`.

## Arquivos

| Pasta | Conteúdo |
|---|---|
| [jacoco/](jacoco/) | relatório JaCoCo completo: abra `jacoco/index.html` num navegador; `jacoco.xml` é o lido pelo SonarCloud |
| [surefire/](surefire/) | um `TEST-*.xml` (formato JUnit) por classe de teste unitário |
| [failsafe/](failsafe/) | um `TEST-*.xml` por classe de integração e o `failsafe-summary.xml` |

## Integração e E2E por classe

| Classe | Casos |
|---|---|
| `com.edualoca.ConcorrenciaReservaIT` | 2 |
| `com.edualoca.ConcorrenciaReservaMultiInstanciaIT` | 2 |
| `com.edualoca.FluxoCompletoNavegadorIT` | 5 |
| `com.edualoca.PersistenciaReinicioIT` | 2 |
| `com.edualoca.persistencia.CatalogoRepositorioJdbcIT` | 3 |
| `com.edualoca.persistencia.HistoricoReservaTransacaoIT` | 4 |
| `com.edualoca.persistencia.ReservaRepositorioJdbcIT` | 6 |

## Cobertura por pacote

| Pacote | Linhas | % | Ramos | % |
|---|---|---|---|---|
| `com.edualoca` | 1/3 | 33,33% | 0/0 | — |
| `com.edualoca.agendamento` | 0/11 | 0,00% | 0/2 | 0,00% |
| `com.edualoca.api` | 49/54 | 90,74% | 12/16 | 75,00% |
| `com.edualoca.api.auditoria` | 12/13 | 92,31% | 8/10 | 80,00% |
| `com.edualoca.api.recurso` | 110/117 | 94,02% | 29/38 | 76,32% |
| `com.edualoca.api.relatorio` | 18/18 | 100,00% | 3/4 | 75,00% |
| `com.edualoca.api.reserva` | 55/59 | 93,22% | 23/26 | 88,46% |
| `com.edualoca.api.retirada` | 39/40 | 97,50% | 12/14 | 85,71% |
| `com.edualoca.api.seguranca` | 62/63 | 98,41% | 37/44 | 84,09% |
| `com.edualoca.api.usuario` | 17/17 | 100,00% | 3/4 | 75,00% |
| `com.edualoca.domain.auditoria` | 30/30 | 100,00% | 15/16 | 93,75% |
| `com.edualoca.domain.comum` | 92/93 | 98,92% | 7/8 | 87,50% |
| `com.edualoca.domain.notificacao` | 41/41 | 100,00% | 6/6 | 100,00% |
| `com.edualoca.domain.recurso` | 257/258 | 99,61% | 75/82 | 91,46% |
| `com.edualoca.domain.relatorio` | 53/54 | 98,15% | 26/30 | 86,67% |
| `com.edualoca.domain.reserva` | 334/341 | 97,95% | 173/179 | 96,65% |
| `com.edualoca.domain.retirada` | 76/76 | 100,00% | 41/42 | 97,62% |
| `com.edualoca.domain.usuario` | 141/144 | 97,92% | 98/112 | 87,50% |
| `com.edualoca.integracao` | 49/51 | 96,08% | 13/16 | 81,25% |
| `com.edualoca.perf` | 18/18 | 100,00% | 8/8 | 100,00% |
| `com.edualoca.persistencia` | 212/217 | 97,70% | 44/52 | 84,62% |

## Como regenerar

No CI, cada run publica os mesmos relatórios como artifacts (`test-reports` e `jacoco-report`, 90 dias). Localmente, `./mvnw -B verify` gera `target/site/jacoco/` e `target/{surefire,failsafe}-reports/` (pré-requisitos no README).
