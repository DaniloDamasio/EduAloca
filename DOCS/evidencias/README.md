# Pacote de evidências

Índice das evidências de cada versão do EduAloca. Toda evidência aponta para o commit da tag: os documentos são
abertos na própria tag (`blob/<tag>/…`), e o CI, o JaCoCo e o SonarCloud são os do push na `main` desse commit.

Este índice e a cópia dos relatórios em [v1.0.0-rc1/](v1.0.0-rc1/) entram na `main` depois da tag, porque o link do
CI só existe depois que o commit é enviado. Nenhum dos dois muda o código: os relatórios são os produzidos pelo CI no
commit da tag. Uma tag publicada nunca é movida. Se for preciso corrigir algo, a próxima versão é `v1.0.0-rc2`, com
uma nova seção abaixo.

## v1.0.0-rc1

- **Tag:** [`v1.0.0-rc1`](https://github.com/DaniloDamasio/EduAloca/tree/v1.0.0-rc1), anotada, no commit
  [`428f282`](https://github.com/DaniloDamasio/EduAloca/commit/428f282f96d8d05f4c09929f84fa8ac921b27c2d) (merge do PR
  #20 na `main`, 13/11/2026)
- **O que entra:** [CHANGELOG.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/CHANGELOG.md)

| Evidência | Resultado | Link |
|---|---|---|
| Execução do CI no commit da tag | ✅ verde: build, 573 unitários e 24 de integração e E2E (5 casos em navegador), 0 falhas, 0 pulados, gate JaCoCo e SonarCloud | [run 43359823197](https://github.com/DaniloDamasio/EduAloca/actions/runs/43359823197) (push na `main`, head `428f282`) |
| Relatório do JaCoCo (artifact) | Linhas **97,03%** (1667/1718), ramos **89,42%** (634/709). Metas: ≥ 80% e ≥ 70% | Artifact [`jacoco-report`](https://github.com/DaniloDamasio/EduAloca/actions/runs/43359823197/artifacts/14007254775) do run acima (o download pede login no GitHub; expira em 11/02/2027). Cópia versionada: [v1.0.0-rc1/jacoco/index.html](v1.0.0-rc1/jacoco/index.html) |
| Relatórios de teste (artifact) | Surefire (50 classes) e Failsafe (7 classes) | Artifact [`test-reports`](https://github.com/DaniloDamasio/EduAloca/actions/runs/43359823197/artifacts/14008475163). Cópia versionada: [v1.0.0-rc1/surefire/](v1.0.0-rc1/surefire/) e [v1.0.0-rc1/failsafe/](v1.0.0-rc1/failsafe/) |
| Quality Gate do SonarCloud | ✅ **OK** na análise `8ed949be` (revisão `428f282`): 0 bugs, 0 vulnerabilidades, 0 code smells, 0 hotspots, 0% de duplicação | [Painel do projeto](https://sonarcloud.io/summary/overall?id=DaniloDamasio_EduAloca&branch=main); Quality Gate desta análise: [API, `analysisId=8ed949be…`](https://sonarcloud.io/api/qualitygates/project_status?analysisId=8ed949be-db1b-4622-acb7-57ab070ee36e) |
| Teste de carga: plano | Metas RNF-01-P a RNF-03-P, modelo de carga e critérios | [DOCS/performance/rnf.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/performance/rnf.md), [perf/reservas.jmx](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/perf/reservas.jmx) |
| Teste de carga: resultado vigente (rodada 02) | Até 400 usuários, 0 dupla reserva em 6.308 janelas disputadas, 0 erros, p95 de criação de 8 a 12 ms | [DOCS/performance/resultado-02.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/performance/resultado-02.md); brutos (`.jtl`) na release [carga-rodada-02](https://github.com/DaniloDamasio/EduAloca/releases/tag/carga-rodada-02) |
| Teste de carga: rodada 01 (histórico) | Primeira rodada, sob disputa | [DOCS/performance/resultado-01.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/performance/resultado-01.md) |
| RTM | 32 de 32 requisitos críticos, com risco, teste e evidência | [DOCS/RTM.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/RTM.md) |
| Registro de defeitos | DEF-001 a DEF-017, com severidade, e todos corrigidos com reteste | [DOCS/defeitos/](https://github.com/DaniloDamasio/EduAloca/tree/v1.0.0-rc1/DOCS/defeitos) |
| Relatório final de qualidade | Recomendação de aceite, com as 4 metas atendidas | [DOCS/relatorio-qualidade.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/relatorio-qualidade.md) |
| Checklist de entrega | Avaliação externa e situação dos bloqueadores | [DOCS/entrega/checklist.md](https://github.com/DaniloDamasio/EduAloca/blob/v1.0.0-rc1/DOCS/entrega/checklist.md) |

### Limites desta versão

- **A carga não mediu o commit da tag.** As duas rodadas usaram versões anteriores: a 02, o jar do `38bbc04`; a 01, uma
  versão sem commit. Os números de carga valem para essas versões, não para o `428f282`. As mudanças posteriores
  (persistência do catálogo, segurança do login, tarefa periódica) estão cobertas pela suíte e pelo CI acima, mas não sob
  carga.
- **Números do relatório de qualidade e da RTM:** foram medidos no CI do PR #18 (head `aa5e48d`), com o mesmo código de
  `src/` e do `pom.xml` do commit da tag. A diferença para este CI é de uma linha coberta (1666 → 1667). A causa não foi
  investigada; as duas medições ficam acima da meta com folga.
- **E2E:** roda no CI. Na máquina do autor, a política de segurança corporativa encerra o Chromium baixado (ver README,
  "Se o navegador não abrir"). Num clone limpo do `428f282`, a suíte sem o E2E passou: 573 unitários, 19 de integração e
  gate atendido.
- **Revisão por pares:** o trabalho é individual, e os PRs não têm revisão de outra pessoa (README, "Autoria e revisão").
