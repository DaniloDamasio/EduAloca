# Revisão ATAM — Painel revisor independente (EduAloca)

> **Natureza deste documento:** camada adicional de revisão sobre
> [arquitetura.md](arquitetura.md) (incluindo a avaliação ATAM já existente em sua §19).
> Não altera, remove nem contradiz o conteúdo normativo de `arquitetura.md`,
> `enunciado.md` (fonte de verdade, imutável) ou dos demais artefatos em
> [REQUISITOS/](REQUISITOS/) e [PERSONAS/](PERSONAS/). Nenhum requisito foi modificado;
> nenhum código foi implementado ou executado.
>
> Elaborado por um painel revisor formado pelos papéis **Arquiteto**, **Dados/ML**,
> **Segurança/LGPD**, **Operações/SRE**, **Custos** e **Facilitador ATAM**, a partir da
> leitura de [enunciado.md](enunciado.md), [REQUISITOS.md](REQUISITOS/REQUISITOS.md),
> [MODELAGEM_DADOS.md](REQUISITOS/MODELAGEM_DADOS.md) e [arquitetura.md](arquitetura.md).

---

## 1. Objeções por papel (fundamentadas, com citação)

### Arquiteto
- **O1** — O documento já trata RF-17 com defesa em profundidade (`EXCLUDE USING gist` +
  `versao`), mas a observação de arquitetura.md §17.1 admite que **reenvio idempotente de
  cliente não é coberto**: duas requisições idênticas do mesmo solicitante, para o mesmo
  recurso/horário, **não** são bloqueadas pela constraint se não houver sobreposição com
  *outro* recurso — isso é inconsistente com a intenção de "exatamente uma reserva aceita"
  se o estímulo for duplicação de rede, não disputa entre dois solicitantes distintos. O
  requisito RF-17 fala em "disputando o mesmo recurso/intervalo" sem distinguir origem do
  duplicado.
- **O2** — T-2 (arquitetura.md §19.7) já identifica que auditoria síncrona na mesma
  transação pressiona RNF-02 (p95 ≤ 3s), mas isso permanece **"hipótese pendente de
  validação via JMeter"** — nenhuma decisão de design (ex.: índice, tamanho de payload de
  auditoria) foi tomada para mitigar, apenas registrado como gap.

### Dados / ML
- **O3 (discordância do facilitador de que isto é "N/A")** — arquitetura.md §12/S2, §17.3
  e §19.4/AT-06 tratam ML como integralmente fora de escopo. Esse enquadramento está
  correto **frente aos artefatos atuais**, mas o papel de Dados chama atenção para um
  ponto que a arquitetura não cobre: o relatório de conflitos evitados (RF-35) depende de
  contar eventos `REJEICAO_SOBREPOSICAO` em `AuditoriaEvento` (arquitetura.md §17.3) — não
  há, em nenhum lugar do documento, uma **definição formal do valor exato de `acao`** usado
  para esse filtro (MODELAGEM_DADOS.md:161 lista exemplos "CRIACAO, APROVACAO, REJEICAO,
  CANCELAMENTO, ATUALIZACAO, INATIVACAO" — **"REJEICAO_SOBREPOSICAO" não aparece nessa
  lista**, apenas "REJEICAO" genérico). Isso é uma inconsistência interna de nomenclatura
  entre arquitetura.md §17.3 e MODELAGEM_DADOS.md:161, não apenas uma lacuna.
- **O4** — Não há decisão sobre retenção/particionamento de `AuditoriaEvento` apesar de ser
  append-only e crescer indefinidamente (D-8 já assume isso deliberadamente, mas nenhum ADR
  trata do impacto em RNF-01/RNF-02 conforme a tabela cresce ao longo do tempo — mesmo em
  80–100 usuários (D-1), auditoria acumula por anos de uso acadêmico).

### Segurança / LGPD
- **O5** — D-8 resolve LGPD como "conformidade mínima dentro do escopo já previsto", mas
  **não cobre direito de acesso/portabilidade/eliminação (Art. 18 LGPD)** para dados de
  `Usuario`/`Professor`. Mesmo sendo um projeto acadêmico, a decisão registrada em D-8 e R11
  (arquitetura.md §19.8) trata apenas do lado técnico (hash de senha, controle de acesso,
  log sem dado pessoal) e explicitamente **não trata de retenção com expurgo** — o papel de
  Segurança registra isso como **objeção aceita, não eliminada**: o risco R11 está
  "mitigado" apenas no sentido de escopo, não de conformidade legal plena.
- **O6** — RNF-06 exige "0% de stacktrace em erro" mas não há no documento nenhum
  componente nomeado de *exception handling* centralizado (ex.: `@ControllerAdvice`) nos
  diagramas de componentes (arquitetura.md §16) — a responsabilidade está descrita em texto
  (§13, linha "Camada de Apresentação") mas **não aparece como componente explícito no C4
  de componentes**, o que é uma lacuna de rastreabilidade entre texto e diagrama.

### Operações / SRE
- **O7** — AT-04/R8 é dado como "mitigado por decisão D-7" (instância única elimina
  concorrência de scheduler). O papel de SRE objeta: isso **troca um risco de corretude por
  um risco de disponibilidade não mensurado** — instância única sem HA (D-5) significa que
  a janela de indisponibilidade do scheduler (ex.: deploy, crash) **atrasa** a sinalização
  de pendência de devolução (RF-26, meta de 1 min) sem que haja qualquer SLA de
  restart/healthcheck documentado. Não há ADR para isso.
- **O8** — Nenhuma seção trata **observabilidade mínima executável**: arquitetura.md §18
  lista "logs de autenticação/erro" e "métricas de latência p95" como requisito, mas não há
  decisão sobre qual stack (ex.: Actuator + Micrometer, já citado implicitamente pela stack
  Spring Boot) nem sobre `health checks` do container único (D-5) — dado que não há
  réplica, a detecção de queda do único processo é puramente operacional/manual, o que
  conflita com a meta de desempenho mensurável do projeto.

### Custos
- **O9** — arquitetura.md §18 linha "Custos" e §7 (Lacuna "Hospedagem") estão formalmente
  "resolvidos" por D-5 (instância única containerizada), mas **nenhum custo foi estimado
  nem para esse cenário mínimo** — mesmo sem exigir orçamento formal, o papel de Custos
  observa que a ausência de qualquer ordem de grandeza (ex.: 1 vCPU/2GB é suficiente para
  80–100 usuários conforme D-1?) deixa a decisão de infraestrutura sem critério de
  validação, mesmo que informal.

### Facilitador ATAM (consolidação)
Os papéis divergem principalmente em **como interpretar risco já "fechado" por decisão do
responsável**: Arquitetura/SRE tratam D-5/D-7 como fechamento aceitável dado o escopo
acadêmico; Segurança insiste que D-8 fecha o escopo mas não a conformidade; Dados aponta
uma inconsistência de nomenclatura que nenhum papel havia notado. Essas divergências **não
são resolvidas aqui** — ficam registradas na seção 5 (Matriz) como gaps e na seção 8
(perguntas).

---

## 2. Verificação de coerência entre C4, sequência, pipeline, utility tree e riscos

| Verificação | Resultado | Evidência |
|---|---|---|
| C4-Contêiner (arquitetura.md §15) ↔ C4-Componentes (§16) | **Coerente** | `Backend API` em §15 expande corretamente em authFilter/controllers/services/repository em §16. |
| C4-Componentes (§16) ↔ Sequência §17.1 (RF-17) | **Coerente** | `ReservaService` → `Repositórios` → `PostgreSQL` bate com SRV→DB da sequência. |
| C4-Componentes (§16) ↔ Observação O6 acima | **Inconsistente** | Tratamento de exceção (RF-38/RNF-06) citado em texto (§13) não tem componente correspondente em §16 — nenhum `GlobalExceptionHandler`/`@ControllerAdvice` nomeado. |
| Utility Tree (§19.4) ↔ Cenários (§19.5) | **Coerente** | Todos os 9 itens da utility tree (AT-01 a AT-09) têm cenário detalhado correspondente em §19.5. |
| Utility Tree/Cenários ↔ Riscos (§10 + §19.8) | **Coerente, com uma lacuna** | AT-05/R10 (dado cadastral desatualizado) está na utility tree e em riscos, mas **não aparece em nenhum ADR nem decisão D-1 a D-8** — risco órfão de decisão (ver §6). |
| Pipeline batch (§17.2) ↔ Aspectos transversais (§18) | **Coerente** | Watermark/checkpoint, idempotência (UPDATE condicional) e retry descritos em §17.2 são retomados identicamente na tabela §18. |
| Pipeline CI (RNF-12) ↔ qualquer diagrama | **Ausente** | Nenhum diagrama (C4 ou sequência) representa o pipeline de CI/CD (build→teste→SonarCloud→gate), apesar de ser um RNF Crítico. Gap de representação, não de decisão. |
| Relatório (§17.3) ↔ MODELAGEM_DADOS.md | **Inconsistente** | Ver O3 — `acao = 'REJEICAO_SOBREPOSICAO'` usado na query de §17.3 não corresponde a nenhum valor de `acao` listado em MODELAGEM_DADOS.md:161. |

---

## 3. ADRs

### ADR-001 — Controle de concorrência em dupla reserva: constraint de exclusão + lock otimista
- **Contexto:** RF-17/Driver #2 exige que, sob duas solicitações simultâneas ao mesmo
  recurso/intervalo, exatamente uma seja aceita (enunciado.md:51).
- **Forças:** corretude sob concorrência real (não simulável só em memória) vs.
  complexidade de mapeamento ORM; necessidade de teste automatizado com ambiente realista
  (Testcontainers, enunciado.md:69,72).
- **Alternativas consideradas:** (a) lock pessimista (`SELECT ... FOR UPDATE`) isolado —
  mais simples, mas serializa escrita por recurso e não cobre todos os caminhos de escrita;
  (b) apenas lock otimista (`versao`) na camada de serviço — não garante corretude se a
  checagem de sobreposição (SELECT) e o INSERT não forem atômicos; (c) apenas constraint de
  banco (`EXCLUDE USING gist`) — suficiente para corretude, mas sem `versao` perde-se
  sinalização de conflito de edição concorrente (RF-13, alteração de reserva).
- **Decisão:** combinar `EXCLUDE USING gist` (tsrange + btree_gist) no banco **com**
  `versao` otimista na aplicação — defesa em profundidade (arquitetura.md §11, §17.1).
- **Consequências:** mapeamento `tsrange` exige SQL nativo/migration ou biblioteca extra
  (trade-off já aceito em T-1, §19.7); ganho de corretude testável via Testcontainers com
  concorrência real.
- **Riscos:** reenvio idempotente de cliente não coberto (O1) — risco **não fechado**,
  candidato a novo ADR se confirmado como requisito.
- **Requisitos:** RF-17, RF-15, RF-16, RNF-08, RNF-09.

### ADR-002 — Auditoria síncrona na mesma transação da mutação de negócio
- **Contexto:** RF-27/RF-28/RF-29 exigem que toda transição de estado e mutação de cadastro
  gere registro de auditoria imutável.
- **Forças:** atomicidade (nenhuma mutação sem par de auditoria, RNF-09 "0 registros
  órfãos") vs. latência de escrita (pressiona RNF-02, p95 ≤ 3s — T-2, §19.7).
- **Alternativas consideradas:** (a) auditoria assíncrona (fila/outbox) — eliminaria
  pressão de latência, mas introduziria janela de inconsistência entre mutação e trilha
  auditável, inaceitável para RNF-09; (b) auditoria em transação separada logo após commit
  — reabre risco de perda de evento em caso de falha entre as duas transações.
- **Decisão:** manter `INSERT auditoria_evento` na mesma transação JDBC do
  `INSERT`/`UPDATE` de negócio (arquitetura.md §13, §17.1).
- **Consequências:** garante RNF-09 por construção; impacto em RNF-02 **não
  quantificado** — ADR permanece com pendência explícita de validação via JMeter.
- **Riscos:** nenhuma mitigação de performance documentada (índice em `auditoria_evento`,
  tamanho de payload) — gap de O2.
- **Requisitos:** RF-27, RF-28, RF-29, RNF-09, RNF-02 (tensão).

### ADR-003 — Notificação pós-commit, best-effort, isolada da transação de negócio
- **Contexto:** RF-33 exige que falha de notificação externa não reverta transição de
  estado.
- **Forças:** robustez transacional vs. garantia de entrega de notificação.
- **Alternativas consideradas:** (a) notificação síncrona dentro da transação — violaria
  RF-33 diretamente (rollback por falha de dependência externa); (b) fila de mensageria com
  retry garantido (ex.: outbox pattern + consumidor) — adicionaria infraestrutura (broker)
  não citada na stack mínima (enunciado.md:109-112), sem requisito de garantia de entrega
  (apenas "notificar", não "garantir entrega").
- **Decisão:** `NotificacaoPort` invocado pós-commit, best-effort, com registro em
  `NotificacaoEnviada` (status ENVIADA/FALHA) para auditabilidade de tentativa, não de
  sucesso garantido.
- **Consequências:** isola corretamente a falha (RF-33 cumprido); notificação pode se
  perder silenciosamente sem reenvio automático (T-3, §19.7) — aceito como gap, não como
  decisão de trade-off resolvida.
- **Riscos:** nenhum requisito de reenvio — se avaliador esperar confiabilidade de
  notificação, há risco de não-conformidade percebida.
- **Requisitos:** RF-31, RF-32, RF-33, RNF-16.

### ADR-004 — Instância única de backend, sem HA/lock distribuído
- **Contexto:** volumetria real estimada em 80–100 usuários (D-1), sem SLA de uptime
  fixado (D-4).
- **Forças:** simplicidade operacional e de custo vs. risco de indisponibilidade sem
  failover; elimina por construção o risco de scheduler concorrente entre réplicas
  (R8/AT-04).
- **Alternativas consideradas:** (a) múltiplas réplicas com lock distribuído (ex.:
  Shedlock, lock via banco) — adicionaria complexidade sem requisito de disponibilidade que
  a justifique; (b) múltiplas réplicas sem lock — reintroduziria R8 sem mitigação.
- **Decisão:** instância única containerizada (D-5), reafirmada para o scheduler em D-7.
- **Consequências:** elimina R8 por construção; introduz janela de indisponibilidade não
  mensurada (O7) sem healthcheck/restart documentado.
- **Riscos:** R9 (ausência de SLA) permanece como risco de negócio aceito, não eliminado.
- **Requisitos:** D-1, D-4, D-5, D-7 (decisões de produto, não RF/RNF diretos);
  RNF-01/02/03 (tensão indireta — degradação sob falha do único processo).

### ADR-005 — Job de sinalização de pendência: scheduler interno, frequência de 30s
- **Contexto:** RF-26 exige sinalização em até 1 min após vencimento, sem orquestrador
  citado na stack mínima.
- **Forças:** simplicidade (compatível com Spring `@Scheduled`) vs. necessidade de margem
  de segurança para a meta de 1 min.
- **Alternativas consideradas:** (a) orquestrador externo (Quartz/cron/Airflow) —
  rejeitado por não constar na stack mínima e por falta de requisito de volume que o
  justifique (arquitetura.md §12/S1); (b) frequência de 1 min exata — rejeitado por não dar
  margem para tempo de processamento do lote.
- **Decisão:** `@Scheduled` interno a cada 30s (D-6), com checkpoint (`ultimoCheckpoint`) e
  `UPDATE` condicional idempotente.
- **Consequências:** cumpre RF-26 com margem; aumenta I/O de polling no banco — aceitável
  para o volume de D-1, sem validação de custo real (O9).
- **Riscos:** nenhum.
- **Requisitos:** RF-26, D-6.

### ADR-006 — LGPD: conformidade mínima dentro do escopo já previsto
- **Contexto:** nenhum artefato normativo (enunciado/REQUISITOS/personas) cita LGPD
  explicitamente; `Usuario`/`Professor` contêm dados pessoais por natureza (nome, e-mail,
  identificador institucional).
- **Forças:** ausência de requisito explícito e de orçamento para pipeline de conformidade
  vs. exposição legal mínima inerente a qualquer sistema com cadastro de pessoas.
- **Alternativas consideradas:** (a) pipeline completo de anonimização/retenção
  configurável — rejeitado por falta de requisito e volume (D-1) que o justifique; (b)
  ignorar LGPD integralmente — rejeitado por expor risco desnecessário dado que controles
  já exigidos (hash de senha, autorização por perfil, log sem stacktrace) cobrem parte do
  problema sem custo adicional.
- **Decisão (D-8):** tratar apenas os controles técnicos já exigidos por outros requisitos
  (RNF-05, RF-01–03, RNF-06) como a conformidade mínima desta fase; retenção indefinida do
  log de auditoria (RF-29/30) prevalece sobre expurgo de dado pessoal.
- **Consequências:** **objeção de Segurança (O5) mantida** — direitos de titular
  (acesso/eliminação) não cobertos; decisão aceita como escopo de projeto acadêmico, não
  como conformidade legal plena.
- **Riscos:** R11 — mitigado apenas em escopo técnico, não em conformidade; reabrir se o
  projeto sair de contexto acadêmico.
- **Requisitos:** RNF-05, RF-01, RF-02, RF-03, RNF-06.

### ADR-007 — PostgreSQL como SGBD
Já formalizado em arquitetura.md §11; o painel **não encontrou objeção** a este ADR —
mantém-se como já escrito (constraint `EXCLUDE USING gist` + Testcontainers como
motivadores centrais, trade-off de mapeamento ORM aceito explicitamente).

### ADR-008 — Prazo de devolução de material = `Reserva.fim`
Já formalizado como D-3 (arquitetura.md §7.1); sem objeção do painel — reaproveita dado
existente, sem nova entidade de configuração, compatível com RF-26.

---

## 4. Matriz Requisito → Decisão → Componente → Evidência

| Requisito | Decisão/ADR | Componente | Evidência esperada (teste/relatório) |
|---|---|---|---|
| RF-15, RF-16 | ADR-001 | `ReservaService`, `ReservaRecurso`, constraint `EXCLUDE` | Teste de integração com Testcontainers cobrindo professor como recurso acessório |
| RF-17, RNF-08 | ADR-001 | `ReservaService`, `versao`, constraint de banco | Teste automatizado com ≥2 requisições concorrentes (enunciado.md:72) |
| RF-11, RF-22 | — (decisão implícita em §13, sem ADR dedicado) | `DisponibilidadeService` | Caso de teste com recurso em manutenção excluído de busca |
| RF-18, RF-19 | — (fluxo de estados §17; sem ADR dedicado — **gap**, ver §6) | `ReservaService`, `AprovacaoController` | Teste de máquina de estados impedindo `EM_USO` sem `APROVADA` |
| RF-23 | D-2 | `ManutencaoController` | Teste de cadastro de manutenção conflitante exigindo confirmação explícita |
| RF-26 | ADR-005, ADR-008 | `RetiradaDevolucaoService`, Job `@Scheduled` | Teste de sinalização em até 1 min, com `freezeTime`/mock de relógio |
| RF-27–30 | ADR-002 | `AuditoriaService` | Teste de ausência de endpoint UPDATE/DELETE sobre auditoria/reserva iniciada |
| RF-31–33 | ADR-003 | `NotificacaoPort` | Teste WireMock com falha simulada, assert de persistência da transação |
| RF-34, RF-35 | — (consulta §17.3; **inconsistência O3 pendente de correção**) | `RelatorioService` | Caso de teste com dados conhecidos + contagem de `REJEICAO_SOBREPOSICAO` |
| RF-36 | — (autorização, sem ADR dedicado) | `authFilter`, `RelatorioController` | Teste de HTTP 403 para Solicitante |
| RF-37, RF-38 | — (sem ADR; tratado em texto §13) | Cliente Web, camada de apresentação | Checklist de breakpoints + revisão de mensagens de erro |
| RNF-04–07 | — (sem ADR dedicado, apesar de Crítico) | `authFilter`, camada de apresentação | SonarCloud 0 Critical/Blocker; teste HTTP 400/401/403 |
| RNF-15 | Stack mínima (enunciado.md:109-112) | Toda a árvore de pacotes | Revisão de estrutura de pacotes |
| D-1, D-5, D-7 | ADR-004 | Infraestrutura de deploy | Nenhuma evidência automatizada aplicável (decisão de infraestrutura, não de código) |
| D-8 / R11 | ADR-006 | `Usuario`, `Professor`, camada de segurança | Nenhuma evidência formal de conformidade LGPD — **gap** |

---

## 5. Requisitos sem cobertura (nenhuma decisão/ADR explícito os endereça diretamente)

- **RF-37 (interface responsiva)** e **RF-39 (OpenAPI)** — mencionados apenas no texto
  (arquitetura.md §13), sem ADR nem componente dedicado nos diagramas C4.
- **RNF-04, RNF-05, RNF-06, RNF-07** — Críticos, mas tratados apenas de forma dispersa no
  texto (§13/§18), sem ADR consolidado de "estratégia de segurança transversal".
- **RNF-10 a RNF-13 (cobertura, RTM, CI, TDD)** — citados como metas, sem nenhum
  componente ou fluxo correspondente nos diagramas (nenhuma representação do pipeline de
  CI, conforme já notado na checagem de coerência, §2).

## 6. Decisões sem requisito direto (assumidas por engenharia, não por RF/RNF)

- Checkpoint `ultimoCheckpoint` no job (arquitetura.md §17.2) — decisão de eficiência, não
  de corretude.
- Ausência de reenvio automático de notificação (T-3) — decorre da ausência de requisito
  de garantia de entrega, não de um requisito que proíba reenvio.
- AT-05/R10 (dado cadastral desatualizado) está na utility tree mas **nenhuma decisão
  (D-1 a D-8) o trata** — risco órfão, nem aceito formalmente nem mitigado.

## 7. Suposições (reafirmadas do documento revisado, não alteradas aqui)

S1 (scheduler interno), S2 (sem ML), S3 ("fluxo de análise" = relatórios agregados), S4
(SPA consumindo API) — mantidas como estão em arquitetura.md §12, sem objeção nova do
painel além de O3 (nomenclatura de `acao` em auditoria).

## 8. Perguntas em aberto para o responsável do projeto

1. A nomenclatura de `acao` em `AuditoriaEvento` deve incluir `REJEICAO_SOBREPOSICAO` como
   valor distinto, ou RF-35 deve ser recalculado a partir de `acao = REJEICAO` combinado
   com outro campo (ex.: motivo)? (O3)
2. Reenvio idêntico do mesmo solicitante para o mesmo recurso/intervalo (timeout de rede)
   deve ser tratado como duplicata a bloquear, exigindo `Idempotency-Key` de cliente? (O1,
   já citado em arquitetura.md §17.1 como risco a esclarecer)
3. Há necessidade de healthcheck/restart automático documentado para a instância única
   (D-5), dado que RF-26 depende da disponibilidade contínua do scheduler? (O7)
4. O direito de eliminação/portabilidade de dados pessoais (LGPD Art. 18) deve ser
   implementado nesta fase, ou permanece fora de escopo acadêmico como D-8 já assume? (O5)

## 9. Riscos aceitos (confirmados nesta rodada, sem nova mitigação proposta)

- **R9** (ausência de SLA de uptime) — aceito por D-4.
- **R11** (LGPD, escopo técnico apenas) — aceito por D-8, com objeção de Segurança mantida
  (O5).
- **R8** (scheduler concorrente) — fechado por construção via D-7 (instância única),
  reabrir se evoluir para múltiplas instâncias.
- **Reenvio idempotente de cliente não coberto** (O1) — aceito como gap até decisão do
  responsável (pergunta 2).

## 10. Próximos experimentos sugeridos

1. Teste de carga JMeter medindo impacto real de ADR-002 (auditoria síncrona) sobre RNF-02
   (p95 ≤ 3s) — valida a hipótese pendente de T-2.
2. Teste de integração simulando reenvio idêntico de cliente (mesmo solicitante, mesmo
   recurso/intervalo, timeout de rede) para decidir se é necessário `Idempotency-Key`
   (pergunta 2).
3. Verificação cruzada entre valores de `acao` usados na query de RF-35 (arquitetura.md
   §17.3) e o enum real implementado em `AuditoriaEvento`, antes de codificar o relatório
   (O3).
4. Prova de conceito de healthcheck/restart do container único (D-5) para estimar janela
   real de indisponibilidade do scheduler (O7).

## 11. Confirmação dos itens solicitados

| Item | Status no documento | Observação do painel |
|---|---|---|
| **Watermark/checkpoint** | Presente (`ultimoCheckpoint`, arquitetura.md §17.2/§18) | Decisão de eficiência, sem requisito direto — confirmado, não é gap de corretude. |
| **Idempotência** | Parcial | Coberta para o job batch (`UPDATE` condicional) e para o par recurso/intervalo (constraint); **não coberta** para reenvio de cliente na criação de reserva (O1). |
| **Lock** | Presente, duplo | Otimista (`versao`) + constraint de exclusão de banco (ADR-001) — defesa em profundidade confirmada. |
| **Retry** | Ausente por decisão | Nenhum retry automático no servidor (cliente e scheduler cobrem via nova tentativa/próxima execução) — decisão implícita, não requisito. |
| **Gates (CI)** | Presente como requisito (RNF-12), **sem representação arquitetural** | Gap de diagrama — nenhum C4/sequência representa o pipeline de CI/CD. |
| **Registry (modelo)** | Não aplicável | Confirmado — sem ML no escopo (S2), devidamente documentado em arquitetura.md §17.3/§18. |
| **Promoção/rollback (modelo)** | Não aplicável | Idem — confirmado como N/A, não omisso. |
| **Observabilidade** | Fraca | Apenas citada em texto (§18); sem componente, sem stack definida, sem healthcheck da instância única (O8). |
| **LGPD** | Parcial | Conformidade técnica mínima via D-8; direitos de titular não cobertos (O5) — risco aceito, não eliminado. |
| **Custo** | Ausente | Nenhuma estimativa, mesmo informal, para o cenário de instância única definido em D-5 (O9). |

---

**Resumo para o responsável:** a arquitetura é internamente coerente na maior parte dos
pontos críticos (concorrência, auditoria, notificação), mas tem quatro gaps que merecem
decisão explícita antes da implementação: (1) nomenclatura de auditoria para RF-35, (2)
idempotência de criação de reserva sob reenvio de cliente, (3) observabilidade/healthcheck
da instância única, e (4) alcance real da conformidade LGPD. Nenhum conteúdo normativo de
`arquitetura.md` foi alterado; este é um documento de revisão adicional sobre sua §19 já
existente.
