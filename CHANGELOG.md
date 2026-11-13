# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/). As versões seguem
[SemVer](https://semver.org/lang/pt-BR/). Uma tag já publicada nunca é movida: uma correção depois de `v1.0.0-rc1` vira
`v1.0.0-rc2`.

## [1.0.0-rc1] - 2026-11-13

Primeira versão candidata, com o escopo completo do [enunciado](DOCS/enunciado.md). As evidências desta versão (CI,
JaCoCo, SonarCloud, carga, RTM, defeitos e relatório de qualidade) ficam no índice
[DOCS/evidencias/README.md](https://github.com/DaniloDamasio/EduAloca/blob/main/DOCS/evidencias/README.md), que é
publicado na `main` depois da tag e aponta para o commit dela.

### Funcionalidades (os 14 requisitos funcionais obrigatórios)

- **Autenticação e autorização por perfil** (Solicitante, Responsável, Administrador): login com senha em
  PBKDF2-SHA256, token opaco com expiração (30 min sem uso, 8 h no total), bloqueio após 5 senhas erradas (429) e
  logout que revoga o token. O Responsável só decide reservas do seu bloco.
- **Cadastro e consulta** de salas, professores com competências e materiais: criação, edição e inativação pelo
  Administrador.
- **Busca de disponibilidade** por tipo, capacidade, localização, competência, quantidade e horário.
- **Reservas:** criação, alteração de horário e cancelamento. Término posterior ao início; sobreposição recusada por sala,
  material e professor.
- **Dupla reserva concorrente:** a constraint de exclusão `ex_reserva_recurso_sem_sobreposicao` (GiST sobre `tstzrange`)
  garante uma única reserva aceita, inclusive com duas instâncias da aplicação.
- **Ciclo de estados** `SOLICITADA → APROVADA → EM_USO → CONCLUIDA`, com rejeitada, cancelada e não compareceu.
  Aprovação obrigatória para recurso restrito. Reserva iniciada não pode ser apagada. Não comparecimento automático no
  fim do horário (tarefa a cada 30 s).
- **Manutenção:** o bloqueio tira o recurso da busca e da reserva. Uma manutenção sobre reserva aprovada exige
  confirmação explícita e cancela essas reservas com aviso.
- **Retirada e devolução** de materiais, vinculadas ao item e à reserva em uso, com lista de devoluções em atraso.
- **Histórico auditável:** toda mudança de estado gravada na mesma transação, com trigger que recusa `UPDATE`/`DELETE`,
  e consulta da auditoria pelo Administrador.
- **Notificação** simulada por padrão, com adaptador HTTP opcional para provedor externo. Uma falha do provedor não
  desfaz a transição de estado.
- **Relatório de utilização:** horas por recurso, carga horária por professor e conflitos evitados no período.
- **Interface web responsiva** para os três perfis (HTML, CSS e JS puros), com erros em `application/problem+json`.
- **Documentação da API** em [DOCS/api.md](DOCS/api.md) e OpenAPI 3 com as 40 operações, mantida em sincronia com o
  código por teste.
- **Persistência em PostgreSQL** de catálogo, reservas e histórico, recarregados na subida.

### Qualidade

- 573 testes unitários (JUnit 5, parametrizados, API em caixa-preta, BDD com Cucumber) e 24 de integração e E2E:
  Testcontainers com PostgreSQL, WireMock para a notificação externa e Playwright para o fluxo completo em navegador.
- Gate do JaCoCo no `verify`: pelo menos 80% de linhas e 70% de ramos.
- CI no GitHub Actions em todo PR e push na `main`, com análise do SonarCloud.
- Mutação: manual nas regras críticas e PIT no domínio (90,6% de mutantes mortos).
- Teste de carga com JMeter: rodada 02, até 400 usuários, 0 dupla reserva.
- RTM com os 32 requisitos críticos, 17 defeitos registrados e corrigidos (DEF-001 a DEF-017), avaliação ATAM e
  relatório final de qualidade.

### Corrigido nesta versão (bloqueadores do [checklist de entrega](DOCS/entrega/checklist.md))

- O `DOCS/enunciado.md` passa a ser versionado. Antes ele estava no `.gitignore`, e 13 links de 11 documentos não abriam
  num clone. Também foram corrigidos os links relativos de `DOCS/REQUISITOS/*.md` para ele.
- O README trazia os números da suíte completa para a variante `-DskipITs`. Agora traz os medidos (85,04% de linhas e
  83,64% de ramos). A variante sem o E2E passa a usar `-Dit.test='*IT,!FluxoCompletoNavegadorIT'`, que não repete os
  unitários no Failsafe.
- A RTM e o relatório de qualidade apontam como evidência vigente o CI do commit desta tag, pelo índice de evidências.

### Limitações conhecidas

- **Revisão por pares:** o trabalho é individual, e os PRs não têm revisão de outra pessoa. A substituição (CI
  obrigatório, mutação e relatório de qualidade) está justificada no README, em "Autoria e revisão".
- **Carga:** a rodada 02 mediu o jar do commit `38bbc04`, anterior à persistência do catálogo, à segurança do login e à
  tarefa periódica. Nenhuma rodada mediu o commit desta tag
  ([relatório de qualidade](DOCS/relatorio-qualidade.md), resumo executivo).
- Continuam só em memória e voltam ao seed num reinício: manutenções, usuários, auditoria, retiradas, notificações e
  tokens ([ADR-010](DOCS/adr/ADR-010-escopo-da-persistencia-postgresql.md)).

[1.0.0-rc1]: https://github.com/DaniloDamasio/EduAloca/tree/v1.0.0-rc1
