# Requisitos — EduAloca

> Derivado de [enunciado.md](enunciado.md) (fonte de verdade, imutável) e das personas em
> [PERSONAS/](PERSONAS/). Os 14 requisitos funcionais obrigatórios do enunciado foram
> decompostos em requisitos atômicos e mensuráveis (RF-xx). Cada linha traz a **Origem**
> (requisito do enunciado + persona que motiva a necessidade), para que a RTM.md possa
> ligar requisito → risco → teste → evidência sem ambiguidade.
>
> Convenção de prioridade: **Crítico** (está nas "Regras críticas" do enunciado ou bloqueia
> entrega), **Alto**, **Médio**.
> Convenção de perfis: **SOL** = Solicitante, **RES** = Responsável, **ADM** = Administrador.

## Requisitos Funcionais

### Bloco [Enunciado #1] — Autenticação e autorização por perfil

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-01 | Autenticar usuários por credenciais (login/senha), recusando acesso inválido. | Enunciado #1; dor comum às 3 personas ("acesso indevido / controle de perfis"). | SOL/RES/ADM | Crítico | 100% das tentativas com credencial inválida retornam HTTP 401, sem indicar qual campo está errado. |
| RF-02 | Autorizar cada endpoint conforme o perfil autenticado. | Enunciado #1; [Administrador](PERSONAS/administrador.md) — "Controle de perfis de acesso". | SOL/RES/ADM | Crítico | 100% dos endpoints protegidos testados retornam HTTP 403 para perfil fora do escopo. |
| RF-03 | Restringir Solicitante a operar apenas sobre as próprias reservas. | Enunciado #4 + regra de perfil; [Solicitante](PERSONAS/solicitante.md) — "cria, altera ou cancela **suas próprias** reservas". | SOL | Crítico | 100% das tentativas de alterar/cancelar reserva de terceiro retornam HTTP 403. |

### Bloco [Enunciado #2] — Cadastro e consulta de salas, professores e materiais

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-04 | CRUD de salas (nome, tipo, capacidade, localização, indicador de restrição). | Enunciado #2; [Administrador](PERSONAS/administrador.md) — "CRUD completo e confiável de salas". | ADM | Crítico | Criação/edição/inativação de sala refletida na consulta em até 1s; 100% dos campos obrigatórios validados. |
| RF-05 | CRUD de professores (nome, identificador, competências). | Enunciado #2; [Administrador](PERSONAS/administrador.md) — "CRUD completo... de professores". | ADM | Crítico | Idem RF-04, aplicado à entidade professor. |
| RF-06 | CRUD de materiais/equipamentos (nome, tipo, quantidade, restrição). | Enunciado #2; [Administrador](PERSONAS/administrador.md) — "CRUD completo... de materiais". | ADM | Crítico | Idem RF-04, aplicado à entidade material. |
| RF-07 | Consulta somente-leitura de salas/professores/materiais ativos para qualquer perfil autenticado. | Enunciado #2; [Solicitante](PERSONAS/solicitante.md) — "Consultar rapidamente a disponibilidade". | SOL/RES/ADM | Alto | Listagem retorna apenas registros ativos; 0% de registros inativados expostos. |

### Bloco [Enunciado #3] — Pesquisa por tipo, capacidade, localização, competência e disponibilidade

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-08 | Buscar salas por tipo, capacidade mínima, localização e disponibilidade em intervalo. | Enunciado #3; [Solicitante](PERSONAS/solicitante.md) — "Busca por tipo, capacidade, localização... de horário". | SOL/RES | Alto | Resposta em até 2s (p95) para base de até 1.000 salas. |
| RF-09 | Buscar professores por competência e disponibilidade em intervalo. | Enunciado #3; [Solicitante](PERSONAS/solicitante.md) — "competência do professor"; [Responsável](PERSONAS/responsavel.md) — "avaliar se professor tem competência necessária". | SOL/RES | Alto | Resposta em até 2s (p95); 100% dos resultados filtrados corretamente por competência exigida. |
| RF-10 | Buscar materiais por tipo e disponibilidade (quantidade livre) em intervalo. | Enunciado #3; [Solicitante](PERSONAS/solicitante.md) — "Busca por tipo... disponibilidade". | SOL/RES | Alto | Resposta em até 2s (p95). |
| RF-11 | Excluir da busca de disponibilidade recursos com reserva ativa sobreposta ou em manutenção. | Enunciado #3 + #8; [Solicitante](PERSONAS/solicitante.md) — dor "descobrir... que havia conflito... ou que o recurso estava em manutenção". | SOL/RES | Crítico | 0% de recursos indisponíveis retornados em 100% dos cenários de teste. |

### Bloco [Enunciado #4] — Criação, alteração e cancelamento de reservas

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-12 | Criar reserva (recurso(s), início, fim, finalidade), nascendo em `SOLICITADA` ou `APROVADA` conforme restrição. | Enunciado #4 + estados; [Solicitante](PERSONAS/solicitante.md) — "Criar reservas... com o mínimo de fricção". | SOL | Crítico | 100% das reservas sem recurso restrito nascem `APROVADA`; 100% com recurso restrito nascem `SOLICITADA`. |
| RF-13 | Alterar reserva própria em estado `SOLICITADA`/`APROVADA`, revalidando conflito e restrição. | Enunciado #4; [Solicitante](PERSONAS/solicitante.md) — "Alterar... reservas já feitas quando os planos mudam". | SOL | Alto | 100% das alterações revalidam sobreposição (RF-15) antes de confirmar. |
| RF-14 | Cancelar reserva própria em qualquer estado anterior a `CONCLUÍDA`, com motivo. | Enunciado #4; [Solicitante](PERSONAS/solicitante.md) — "Cancelar uma reserva porque a aula foi remarcada". | SOL | Crítico | 100% dos cancelamentos exigem motivo não vazio e geram transição auditada. |

### Bloco [Enunciado #5] — Detecção de sobreposição de horários, inclusive na agenda do professor

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-15 | Rejeitar reserva cujo intervalo se sobreponha a outra reserva ativa do mesmo recurso. | Enunciado #5 + Regra crítica "Reservas do mesmo recurso não podem se sobrepor"; [Solicitante](PERSONAS/solicitante.md) — dor "conflito de horário". | SOL/RES/ADM | Crítico | HTTP 409 em 100% dos casos de sobreposição testados, com recurso e horário conflitante na mensagem. |
| RF-16 | Validar sobreposição na agenda do professor mesmo quando acessório de outra reserva. | Enunciado #5 (texto explícito "inclusive na agenda do professor"); [Solicitante](PERSONAS/solicitante.md) — "se um professor... está disponível para atividade conjunta". | SOL/RES | Crítico | 100% dos casos de professor duplamente alocado (como titular ou convidado) são bloqueados. |

### Bloco [Enunciado #6] — Proteção contra dupla reserva em solicitações simultâneas

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-17 | Sob concorrência, aceitar exatamente uma reserva disputando o mesmo recurso/intervalo. | Enunciado #6 + Regra crítica; [Solicitante](PERSONAS/solicitante.md) — "Perder a reserva... sem entender o motivo". | SOL/RES/ADM | Crítico | Teste automatizado com ≥2 requisições concorrentes: exatamente 1 aceita, demais com HTTP 409, 0 duplicatas. |

### Bloco [Enunciado #7] — Aprovação obrigatória para recursos restritos

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-18 | Reserva com recurso restrito permanece `SOLICITADA` até avaliação; não pula para `EM_USO` sem `APROVADA`. | Enunciado #7 + estados; Regra "Somente responsáveis aprovam recursos restritos". | RES | Crítico | 100% das reservas restritas passam obrigatoriamente pelo estado `APROVADA` antes de `EM_USO`. |
| RF-19 | Somente Responsável/Administrador aprovam ou rejeitam recursos restritos. | Enunciado #7; [Responsável](PERSONAS/responsavel.md) — "Avaliar e decidir solicitações de recursos restritos". | RES/ADM | Crítico | 100% das tentativas de aprovação por Solicitante retornam HTTP 403. |
| RF-20 | Rejeição exige justificativa textual não vazia, visível ao Solicitante. | [Responsável](PERSONAS/responsavel.md) — "Rejeitar... com justificativa"; [Solicitante](PERSONAS/solicitante.md) — dor "falta de clareza sobre por que foi rejeitada". | RES | Alto | 100% das rejeições sem justificativa são bloqueadas pela API (HTTP 400). |

### Bloco [Enunciado #8] — Bloqueio de recursos em manutenção

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-21 | Registrar período de manutenção/bloqueio (recurso, início, fim, motivo). | Enunciado #8; [Administrador](PERSONAS/administrador.md) — "Colocar um laboratório em manutenção... bloqueando automaticamente". | ADM | Crítico | Período registrado reflete-se em disponibilidade (RF-11) em até 1s. |
| RF-22 | Rejeitar reserva cujo intervalo se sobreponha a manutenção ativa. | Enunciado #8 + Regra crítica "Recursos em manutenção não podem ser reservados". | SOL/RES/ADM | Crítico | HTTP 409 em 100% dos casos de sobreposição com manutenção testados. |
| RF-23 | Bloquear ou exigir confirmação explícita ao cadastrar manutenção que colida com reservas já `APROVADA`/`EM_USO`. | Enunciado #8; [Administrador](PERSONAS/administrador.md) — dor "Esquecer de cadastrar manutenções, permitindo reservas em recursos indisponíveis" (consistência inversa). | ADM | Alto | 100% dos cadastros de manutenção conflitante exigem confirmação explícita ou são recusados. |

### Bloco [Enunciado #9] — Registro de retirada e devolução de materiais e equipamentos

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-24 | Registrar retirada de material vinculado a reserva `EM_USO` (data/hora, quem retirou). | Enunciado #9; [Responsável](PERSONAS/responsavel.md) — "Registrar a retirada de um equipamento". | RES | Crítico | 100% das retiradas vinculam reserva + item + autor + timestamp. |
| RF-25 | Registrar devolução de material (data/hora, condição do item). | Enunciado #9; [Responsável](PERSONAS/responsavel.md) — "confirmar sua devolução". | RES | Crítico | 100% das devoluções encerram o ciclo de retirada aberto correspondente. |
| RF-26 | Sinalizar material retirado e não devolvido após término da reserva. | [Responsável](PERSONAS/responsavel.md) — dor "Materiais retirados sem registro adequado de devolução". | RES | Alto | Pendência aparece em listagem do Responsável em até 1 min após vencimento. |

### Bloco [Enunciado #10] — Histórico auditável de mudanças

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-27 | Gerar registro de auditoria imutável em toda transição de estado de reserva. | Enunciado #10 + Regra "Toda mudança de estado deve gerar auditoria"; [Responsável](PERSONAS/responsavel.md) — "não saber quem aprovou o quê". | SOL/RES/ADM | Crítico | 100% das transições de estado possuem registro de auditoria correspondente (verificado por teste). |
| RF-28 | Gerar registro de auditoria em toda alteração de cadastro (sala, professor, material, usuário, bloqueio). | Enunciado #10; [Administrador](PERSONAS/administrador.md) — "Investigar, via histórico auditável, por que uma reserva foi cancelada". | ADM | Crítico | 100% das operações de escrita em cadastro geram entrada de auditoria. |
| RF-29 | Impedir edição/exclusão de registros de auditoria por qualquer perfil. | Enunciado #10 (implícito em "histórico auditável"); integridade de rastreabilidade. | SOL/RES/ADM | Crítico | Ausência verificada de endpoint PUT/PATCH/DELETE sobre o recurso de auditoria. |
| RF-30 | Impedir exclusão física de reservas já iniciadas (fora de `SOLICITADA`). | Regra crítica "Reservas iniciadas não podem ser apagadas". | SOL/RES/ADM | Crítico | 100% das tentativas de DELETE sobre reserva iniciada retornam HTTP 403/405. |

### Bloco [Enunciado #11] — Notificação simulada ou integração com API externa

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-31 | Notificar o Solicitante a cada mudança de estado da própria reserva. | Enunciado #11; [Solicitante](PERSONAS/solicitante.md) — "Notificações sobre mudanças de status da própria solicitação". | SOL | Alto | Notificação disparada em até 5s após a transição ser persistida, em 100% das transições. |
| RF-32 | Notificar o Responsável competente ao surgir nova solicitação restrita pendente. | Enunciado #11; [Responsável](PERSONAS/responsavel.md) — "Notificações quando novas solicitações restritas chegam". | RES | Alto | Notificação disparada em até 5s após criação da solicitação restrita. |
| RF-33 | Garantir que falha de notificação externa não reverta a transição de estado. | Enunciado #11 (integração externa) + robustez; evita acoplamento entre efeito colateral e regra de negócio. | SOL/RES/ADM | Alto | 100% das transições persistem mesmo com endpoint de notificação indisponível (validado via WireMock com falha simulada). |

### Bloco [Enunciado #12] — Relatório de utilização por recurso, carga horária alocada e conflitos evitados

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-34 | Gerar relatório de utilização (reservas e carga horária) por recurso em intervalo de datas. | Enunciado #12; [Administrador](PERSONAS/administrador.md) — "Gerar um relatório mensal de utilização de salas". | ADM/RES | Alto | Relatório reflete 100% das reservas `CONCLUÍDA`/`EM_USO` do período, validado por caso de teste com dados conhecidos. |
| RF-35 | Gerar relatório de conflitos evitados (rejeições por sobreposição/dupla reserva) no período. | Enunciado #12; [Administrador](PERSONAS/administrador.md) — "visão consolidada do uso dos recursos... para apoiar decisões institucionais". | ADM | Médio | Contagem de conflitos evitados bate com número de rejeições HTTP 409 registradas em auditoria no período. |
| RF-36 | Restringir acesso aos relatórios a Responsável e Administrador. | Enunciado #1 (autorização) aplicado a #12; escopo de perfil do [Solicitante](PERSONAS/solicitante.md) não inclui relatórios. | ADM/RES | Alto | 100% das tentativas de acesso por Solicitante retornam HTTP 403. |

### Bloco [Enunciado #13] — Interface responsiva e mensagens de erro compreensíveis

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-37 | Interface adaptável de 360px a 1920px de viewport sem quebra de layout. | Enunciado #13; [Solicitante](PERSONAS/solicitante.md) — "utilizável tanto no computador... quanto no celular". | SOL/RES/ADM | Alto | Validado sem quebra visual em ao menos 3 breakpoints (360px, 768px, 1920px). |
| RF-38 | Mensagens de erro em linguagem natural, sem stacktrace/código técnico cru. | Enunciado #13; [Solicitante](PERSONAS/solicitante.md) — dor "Mensagens de erro técnicas ou confusas". | SOL/RES/ADM | Crítico | 100% das respostas de erro de negócio (4xx) revisadas contêm mensagem compreensível, sem termo técnico interno. |

### Bloco [Enunciado #14] — Documentação de API ou dos fluxos públicos do sistema

| ID | Requisito | Origem (enunciado / persona) | Perfil | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RF-39 | Documentar 100% dos endpoints públicos (OpenAPI/Swagger): parâmetros, respostas, exemplos. | Enunciado #14; entregável "README.md e documentação dos fluxos ou da API". | SOL/RES/ADM | Alto | 100% dos endpoints expostos aparecem na especificação OpenAPI gerada/publicada. |

---

## Requisitos Não Funcionais

Origem comum: seção "Estratégia obrigatória de qualidade" e "Metas mensuráveis" do
enunciado (não derivam de uma persona específica, mas das metas de avaliação do projeto).

| ID | Requisito | Origem (enunciado) | Categoria | Prioridade | Critério de aceite mensurável |
|---|---|---|---|---|---|
| RNF-01 | Endpoints de consulta respondem em até 2s (p95) sob carga de até 50 usuários simultâneos. | "Não funcionais: JMeter" | Desempenho | Alto | p95 ≤ 2s medido em plano de teste JMeter documentado. |
| RNF-02 | Endpoints de escrita de reserva respondem em até 3s (p95) sob a mesma carga. | "Não funcionais: JMeter" | Desempenho | Alto | p95 ≤ 3s medido em plano de teste JMeter documentado. |
| RNF-03 | Suportar pico de ≥100 req/min concorrentes em criação de reserva sem erro 5xx. | "Não funcionais: JMeter" | Desempenho | Alto | 0% de respostas 5xx no cenário de pico do plano de carga. |
| RNF-04 | Validar toda entrada (tipo, formato, limites) antes de processar regra de negócio. | "Não funcionais: validação de entrada" | Segurança | Crítico | 100% dos campos obrigatórios/formatados testados retornam HTTP 400 quando inválidos. |
| RNF-05 | Armazenar senha com hash (ex.: BCrypt), nunca texto plano. | "Não funcionais: autorização" + segurança implícita em #1 | Segurança | Crítico | 0% de senhas em texto plano no schema do banco (verificado por inspeção). |
| RNF-06 | Não expor stacktrace/detalhes internos em resposta de erro ao cliente. | "Não funcionais: tratamento seguro de erros" | Segurança | Crítico | 0% das respostas de erro observadas contêm stacktrace ou nome de classe interna. |
| RNF-07 | 0 vulnerabilidades Crítica/Bloqueadora reportadas pelo SonarCloud na branch principal. | "CI e análise estática... integrado ao SonarCloud"; Meta "0 bugs ou vulnerabilidades críticas" | Segurança | Crítico | Relatório SonarCloud da branch principal com 0 issues Critical/Blocker. |
| RNF-08 | Mecanismo de dupla reserva consistente sob falha parcial (ex.: timeout). | "Concorrência: teste automatizado comprovando a proteção" | Confiabilidade | Crítico | Teste de integração com Testcontainers comprova ausência de estado intermediário inconsistente. |
| RNF-09 | 100% de integridade referencial entre reservas, recursos e auditoria. | "Histórico auditável" + "Ambiente realista" | Confiabilidade | Alto | 0 registros órfãos detectados em verificação de integridade automatizada. |
| RNF-10 | Cobertura de linhas ≥80% e branches ≥70% (JaCoCo). | Meta mensurável explícita | Qualidade/Teste | Crítico | Relatório JaCoCo da branch principal atende aos dois limiares. |
| RNF-11 | 100% dos requisitos críticos rastreados na RTM.md com teste e evidência. | Meta mensurável explícita; entregável RTM.md | Qualidade/Teste | Crítico | RTM.md sem lacunas para qualquer RF marcado "Crítico". |
| RNF-12 | Pipeline de CI executa build + testes + análise estática em todo PR, bloqueando merge em falha. | "CI e análise estática: GitHub Actions em pull requests" | Qualidade/Processo | Crítico | 100% dos PRs mesclados possuem execução de CI verde registrada. |
| RNF-13 | Ao menos 1 funcionalidade desenvolvida com evidência documentada de TDD. | "TDD: Ao menos uma funcionalidade nova... com evidências da técnica" | Qualidade/Processo | Alto | Evidência (commits/relatório) do ciclo red-green-refactor anexada à documentação. |
| RNF-14 | Fluxos críticos (criar reserva, aprovar/rejeitar) operáveis via teclado/leitor de tela. | "Interface responsiva" (#13) interpretado com padrão mínimo de acessibilidade | Usabilidade | Médio | Fluxos críticos navegáveis 100% via teclado, conforme checklist WCAG 2.1 nível A. |
| RNF-15 | Arquitetura MVC em Java 21 / Spring Boot 4.1.1 / Maven, camadas separadas. | "Stack mínima necessária" | Arquitetura | Crítico | Estrutura de pacotes revisada confirma separação apresentação/serviço/persistência. |
| RNF-16 | Integrações externas isoladas por interface, substituíveis por mock (WireMock) em teste. | "Ambiente realista: WireMock para integrações externas" | Arquitetura/Teste | Alto | 100% das integrações externas possuem dublê de teste (WireMock) configurado na suíte. |
