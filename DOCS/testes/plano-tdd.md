# Plano de Ciclo de TDD — Inventário de Regras de Negócio e Casos de Teste

> **Natureza deste documento:** inventário preparatório para um ciclo de TDD (red-green-refactor).
> Não implementa código nem testes — apenas organiza, a partir dos artefatos existentes, todas as
> regras de negócio identificáveis, cada uma com sua origem, e os casos de teste que a exercitariam
> (caminho feliz, limites, entradas inválidas, conflitos e estados proibidos).
>
> **Fontes consultadas (únicas fontes de verdade usadas):**
> - [`DOCS/enunciado.md`](../enunciado.md) — documento imutável, autoritativo em caso de conflito.
> - [`DOCS/REQUISITOS/REQUISITOS.md`](../REQUISITOS/REQUISITOS.md) — RF-01 a RF-39, RNF-01 a RNF-16.
> - [`DOCS/REQUISITOS/JORNADAS.md`](../REQUISITOS/JORNADAS.md) — J1 a J9.
> - [`DOCS/REQUISITOS/OBJETIVOS.md`](../REQUISITOS/OBJETIVOS.md).
> - [`DOCS/REQUISITOS/MODELAGEM_DADOS.md`](../REQUISITOS/MODELAGEM_DADOS.md) e
>   [`MODELAGEM_DADOS.dbml`](../REQUISITOS/MODELAGEM_DADOS.dbml).
> - [`DOCS/PERSONAS/administrador.md`](../PERSONAS/administrador.md),
>   [`responsavel.md`](../PERSONAS/responsavel.md), [`solicitante.md`](../PERSONAS/solicitante.md).
> - [`DOCS/arquitetura.md`](../arquitetura.md) — §1 a §19 (inclui ADR-like decisões D-1 a D-8 e
>   avaliação ATAM interna §19).
> - [`DOCS/REVISAO_ATAM.md`](../REVISAO_ATAM.md) — ADR-001 a ADR-008, objeções O1–O9, riscos,
>   perguntas em aberto.
>
> **Artefatos procurados e não encontrados** (registrado por transparência, ver também §3):
> - `RTM.md` — não existe no repositório (busca `find . -iname "*rtm*"` sem resultado). O
>   enunciado (linha 99) e OBJETIVOS.md (linha 53/57) o citam como entregável obrigatório, mas
>   ainda não foi criado.
> - Qualquer ADR formal em `DOCS/ADR/` ou similar — não existe diretório de ADR; as decisões
>   arquiteturais equivalentes a ADR estão registradas em `REVISAO_ATAM.md` §3 (ADR-001 a
>   ADR-008) e em `arquitetura.md` §7.1 (D-1 a D-8).
> - Código de produção além do boilerplate Spring Boot padrão (`EduAlocaApplication.java`) —
>   confirmado via `find src -type f`: não há controllers, services, repositories, entidades
>   JPA nem migrations implementados.
> - Testes existentes além do teste de contexto padrão (`EduAlocaApplicationTests.contextLoads()`)
>   — não há suíte de testes unitários/integração/E2E implementada.
> - `pom.xml` não declara ainda dependências necessárias para o domínio (Spring Data JPA,
>   Spring Security, PostgreSQL driver/Testcontainers, WireMock, JUnit 5 parametrizado além do
>   starter-test padrão) — apenas `spring-boot-starter-web` e `spring-boot-starter-test`.
>
> **Como ler este documento:** cada regra tem um identificador de origem (RF-xx / RNF-xx / regra
> crítica do enunciado / decisão Dx / ADR-00x) e uma lista de casos de teste categorizados. Nenhum
> valor numérico ou comportamento foi inventado além do que consta nas fontes acima; onde a fonte é
> omissa, o caso de teste está marcado como **[gap]** e a pergunta correspondente está detalhada na
> §3.

---

## 0. Pré-requisito ainda não resolvido: nenhum código de domínio existe

Como não há entidades, enums de estado, controllers nem repositórios implementados, todos os
casos de teste abaixo descrevem **comportamento esperado da regra de negócio**, não uma suíte
executável hoje. Isso é esperado para esta etapa (inventário pré-TDD) e está consistente com o
cabeçalho de `arquitetura.md` ("Nenhum código foi implementado nesta etapa").

---

## 1. Regras críticas (seção "Regras críticas" do enunciado — prioridade máxima)

Estas quatro regras são citadas literalmente no enunciado (`enunciado.md:48-61`) e reafirmadas
como "Restrições críticas (obrigatórias)" em `arquitetura.md §9`.

### 1.1 RC-01 — "O término deve ser posterior ao início"

**Origem:** enunciado.md:49 ("Tempo e concorrência"); RF-12 (REQUISITOS.md:45, "início, fim");
modelado como `CHECK (fim > inicio)` em MODELAGEM_DADOS.md:99 e MODELAGEM_DADOS.dbml:69.

| Caso | Categoria | Entrada | Resultado esperado |
|---|---|---|---|
| RC-01-T1 | Caminho feliz | `inicio = 10:00`, `fim = 12:00` (mesmo dia) | Reserva aceita para validação de intervalo (segue para demais checagens: sobreposição, restrição etc.). |
| RC-01-T2 | Valor-limite | `fim = inicio + 1 segundo/minuto` (menor intervalo válido) — **[gap: granularidade mínima de intervalo não definida em nenhum artefato]** | Aceito como válido quanto à regra `fim > inicio`; granularidade mínima real é questão em aberto (§3, Q1). |
| RC-01-T3 | Entrada inválida | `fim = inicio` (intervalo de duração zero) | Rejeitado — HTTP 400 (RNF-04, "validar toda entrada... antes de processar regra de negócio"), mensagem compreensível (RF-38). |
| RC-01-T4 | Entrada inválida | `fim < inicio` | Rejeitado — HTTP 400, mensagem identificando o campo inválido sem termo técnico (RF-38, RNF-04). |
| RC-01-T5 | Entrada inválida | `inicio` ou `fim` ausente/nulo | Rejeitado — HTTP 400 (RNF-04, "100% dos campos obrigatórios... testados retornam HTTP 400"). |
| RC-01-T6 | Estado proibido | Tentativa de alterar reserva existente (RF-13) definindo novo `fim <= inicio` | Rejeitado — HTTP 400; alteração não persistida. |

### 1.2 RC-02 — "Reservas do mesmo recurso não podem se sobrepor — sala, material ou professor"

**Origem:** enunciado.md:50; RF-15 (sobreposição geral), RF-16 (agenda do professor mesmo como
recurso acessório); modelado via `ReservaRecurso` + constraint `EXCLUDE USING gist`
(MODELAGEM_DADOS.md:102-119; arquitetura.md §11; ADR-001 em REVISAO_ATAM.md §3).

| Caso | Categoria | Entrada | Resultado esperado |
|---|---|---|---|
| RC-02-T1 | Caminho feliz | Reserva A: sala X, 10h-12h. Reserva B: sala X, 13h-14h (sem sobreposição) | Ambas aceitas — RF-15 não impede intervalos disjuntos. |
| RC-02-T2 | Valor-limite | Reserva A: sala X, 10h-12h. Reserva B: sala X, 12h-13h (fim de A == início de B, contíguos) | Aceitas (sem sobreposição real) — **[gap: não há confirmação explícita no enunciado/REQUISITOS de que intervalos fechados/abertos nas bordas (`[10,12)` vs `[10,12]`) são tratados como não sobrepostos; assume-se semântica de intervalo semiaberto por ser a convenção usual de `tsrange`, mas isso não está escrito em nenhum artefato]** — ver §3, Q2. |
| RC-02-T3 | Conflito | Reserva A: sala X, 10h-12h, estado `APROVADA`. Reserva B: sala X, 11h-13h (sobreposição parcial) | Reserva B rejeitada — HTTP 409, mensagem identificando recurso e horário conflitante (RF-15, REQUISITOS.md:53). |
| RC-02-T4 | Conflito | Reserva A: sala X, 10h-12h. Reserva B: sala X, 09h-13h (B engloba totalmente A) | Rejeitada — HTTP 409 (RF-15). |
| RC-02-T5 | Conflito | Reserva A: sala X, 10h-12h. Reserva B: sala X, 10h-12h (intervalos idênticos) | Rejeitada — HTTP 409 (RF-15). |
| RC-02-T6 | Conflito (agenda do professor como recurso acessório) | Reserva A: Professor P como recurso titular de uma atividade, 10h-12h. Reserva B: sala Y + Professor P como recurso convidado, 11h-13h | Rejeitada — HTTP 409; sobreposição detectada na agenda de P mesmo P não sendo o recurso "principal" de B (RF-16, enunciado.md:35 "inclusive na agenda do professor"). |
| RC-02-T7 | Caminho feliz (multi-recurso sem conflito) | Reserva com sala X + professor P + material M, todos livres no intervalo | Aceita — nenhum dos três recursos tem sobreposição. |
| RC-02-T8 | Conflito (apenas um dos múltiplos recursos em conflito) | Reserva com sala X (livre) + professor P (ocupado no intervalo) | Rejeitada — HTTP 409 citando especificamente o recurso em conflito (professor P), não a sala (RF-15/RF-16 + RF-38 mensagem compreensível). |
| RC-02-T9 | Estado proibido (reserva cancelada/rejeitada não bloqueia) | Reserva A: sala X, 10h-12h, estado `CANCELADA` ou `REJEITADA`. Reserva B: sala X, 10h-12h | Aceita — regra fala em "reserva ativa" (RF-15: "sobreponha a outra reserva **ativa**"); reservas canceladas/rejeitadas não contam como ativas. **[gap: REQUISITOS.md não define explicitamente quais estados contam como "ativa" para este fim — assume-se SOLICITADA/APROVADA/EM_USO por exclusão, mas isso não está escrito literalmente]** — ver §3, Q3. |
| RC-02-T10 | Conflito (manutenção, inter-regra) | Sala X com manutenção registrada 10h-18h. Tentativa de reserva de sala X, 11h-12h | Rejeitada — HTTP 409 (RF-22, RC-04 abaixo; sobreposição com manutenção, não com outra reserva). |

### 1.3 RC-03 — "Duas solicitações simultâneas devem produzir uma única reserva aceita"

**Origem:** enunciado.md:51 ("Concorrência"); RF-17; RNF-08; ADR-001 (REVISAO_ATAM.md §3);
estratégia obrigatória de qualidade "teste automatizado comprovando a proteção contra dupla
reserva" (enunciado.md:72).

| Caso | Categoria | Entrada | Resultado esperado |
|---|---|---|---|
| RC-03-T1 | Caminho feliz (sem disputa) | Uma única requisição de reserva para recurso livre | Aceita normalmente (não é um cenário de concorrência, mas baseline de comparação). |
| RC-03-T2 | Conflito sob concorrência real | 2 requisições simultâneas (threads/processos distintos) para o mesmo recurso e mesmo intervalo, solicitantes distintos (A e B) | Exatamente 1 aceita (HTTP 201/200), a outra rejeitada com HTTP 409; 0 duplicatas persistidas no banco (RF-17, critério de aceite mensurável em REQUISITOS.md:60). |
| RC-03-T3 | Conflito sob concorrência real (N requisições) | N ≥ 2 requisições simultâneas disputando o mesmo recurso/intervalo (ex.: N=5) | Exatamente 1 aceita; N-1 rejeitadas com HTTP 409; teste deve rodar contra banco real (Testcontainers), não em memória (enunciado.md:69; RNF-08). |
| RC-03-T4 | Estado proibido (inconsistência sob falha parcial) | Timeout/falha de rede durante a transação de uma das requisições concorrentes | Nenhum estado intermediário inconsistente deve ser observável — rollback atômico garante 0 ou 1 reserva persistida, nunca uma reserva "parcial" (RNF-08, "Testcontainers comprova ausência de estado intermediário inconsistente"). |
| RC-03-T5 | Entrada inválida / fora de escopo documentado | Reenvio idêntico do **mesmo** solicitante, mesmo recurso, mesmo intervalo (retry de rede, não dois solicitantes distintos) | **[gap — risco O1 não fechado, REVISAO_ATAM.md §1 "Arquiteto"]**: a constraint de exclusão **não bloqueia** esse caso se não houver sobreposição com outro recurso; o comportamento esperado (criar duplicata vs. exigir `Idempotency-Key`) não está definido em nenhum artefato — ver §3, Q4. Não escrever este teste como "deve rejeitar" até decisão do responsável. |

### 1.4 RC-04 — "Recursos em manutenção não podem ser reservados"

**Origem:** enunciado.md:52; RF-22 (REQUISITOS.md:75); RF-21 (registro de manutenção,
REQUISITOS.md:74); RF-11 (exclusão de busca, REQUISITOS.md:39); RF-23 (manutenção conflitante
com reserva existente, REQUISITOS.md:76); decisão D-2 (arquitetura.md §7.1) — exigir confirmação
explícita, não bloquear automaticamente.

| Caso | Categoria | Entrada | Resultado esperado |
|---|---|---|---|
| RC-04-T1 | Caminho feliz | Administrador registra manutenção para sala X, 01/03 a 07/03, motivo "troca de ar-condicionado" | Período persistido; reflexo em disponibilidade em até 1s (RF-21); auditoria gerada (RF-28). |
| RC-04-T2 | Conflito | Sala X em manutenção 01/03-07/03. Tentativa de reserva de sala X em 03/03, 10h-12h | Rejeitada — HTTP 409, em 100% dos casos testados (RF-22, critério de aceite). |
| RC-04-T3 | Valor-limite | Reserva solicitada exatamente no limite da manutenção: `fim da reserva == inicio da manutenção` ou `inicio da reserva == fim da manutenção` | **[gap: não há definição explícita de fronteira fechada/aberta para manutenção, análoga à RC-02-T2]** — ver §3, Q2 (mesma ambiguidade de semântica de intervalo). |
| RC-04-T4 | Conflito (ordem inversa: manutenção cadastrada depois da reserva) | Reserva `APROVADA` já existe para sala X, 10/03, 10h-12h. Administrador tenta cadastrar manutenção de sala X, 10/03, 09h-13h (sobrepõe reserva aprovada) | Sistema exige confirmação explícita do Administrador antes de aceitar o cadastro de manutenção (decisão D-2); sem confirmação, cadastro é recusado (RF-23, critério "100% dos cadastros... exigem confirmação explícita ou são recusados"). |
| RC-04-T5 | Conflito (idem, reserva `EM_USO`) | Mesmo cenário de RC-04-T4, mas a reserva conflitante está em estado `EM_USO` | Mesmo comportamento de RC-04-T4 (RF-23 cita `APROVADA`/`EM_USO` explicitamente). |
| RC-04-T6 | Estado "não conflito" (reserva em estado terminal) | Reserva em estado `CANCELADA`/`REJEITADA`/`CONCLUIDA`/`NAO_COMPARECEU` no mesmo intervalo da nova manutenção | Cadastro de manutenção aceito sem exigir confirmação — reserva não ativa não bloqueia (mesma lógica de "ativa" de RC-02-T9, mesmo gap de definição formal — §3, Q3). |
| RC-04-T7 | Entrada inválida | Manutenção com `fim <= inicio` | Rejeitada — HTTP 400 (RNF-04); mesma regra RC-01 aplicada a `Manutencao` (MODELAGEM_DADOS.md não define `CHECK` explícito para `Manutencao`, mas a regra de negócio "término posterior ao início" é geral o suficiente para ser esperada — **[gap: não há confirmação literal de que RC-01 se aplica a `Manutencao`, apenas a `Reserva`]**, ver §3, Q5). |
| RC-04-T8 | Entrada inválida | Cadastro de manutenção sem motivo | Rejeitado — HTTP 400 (campo `motivo` é `not null` no DBML, MODELAGEM_DADOS.dbml:101; RNF-04). |

---

## 2. Requisitos funcionais (RF-01 a RF-39) — regras derivadas, com origem RF

### Bloco Autenticação e autorização (RF-01 a RF-03)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-01-T1 | RF-01 | Caminho feliz | Login/senha válidos | Autenticado (sessão/token emitido). |
| RF-01-T2 | RF-01 | Entrada inválida | Senha incorreta | HTTP 401, sem indicar qual campo está errado (REQUISITOS.md:19, "sem indicar qual campo está errado"). |
| RF-01-T3 | RF-01 | Entrada inválida | E-mail/login inexistente | HTTP 401 — mesma resposta genérica de RF-01-T2, para não revelar existência de conta (**[gap: não há exigência explícita de resposta idêntica para "senha errada" vs "usuário inexistente"; inferido apenas da frase "sem indicar qual campo está errado"]** — ver §3, Q6). |
| RF-01-T4 | RF-01, RNF-05 | Estado proibido | Inspeção do schema do banco | 0% de senhas em texto plano — hash BCrypt (RNF-05). |
| RF-02-T1 | RF-02 | Caminho feliz | Usuário ADMINISTRADOR chama endpoint de cadastro de sala | Autorizado (HTTP 2xx). |
| RF-02-T2 | RF-02 | Estado proibido | Usuário SOLICITANTE chama endpoint de cadastro de sala (ADM-only) | HTTP 403, em 100% dos endpoints protegidos testados fora do escopo do perfil (REQUISITOS.md:20). |
| RF-02-T3 | RF-02 | Estado proibido | Usuário RESPONSAVEL chama endpoint de CRUD de usuários (ADM-only) | HTTP 403. |
| RF-03-T1 | RF-03 | Caminho feliz | Solicitante altera/cancela a própria reserva | Permitido. |
| RF-03-T2 | RF-03 | Estado proibido | Solicitante A tenta alterar/cancelar reserva de Solicitante B | HTTP 403, em 100% dos casos (REQUISITOS.md:21; cenário AT-09, arquitetura.md). |
| RF-03-T3 | RF-03 | Estado proibido | Solicitante tenta **consultar** (GET) reserva de outro solicitante | **[gap: RF-03 fala em "alterar/cancelar"; não é explícito se GET de reserva alheia também é 403 ou se é permitido só leitura]** — ver §3, Q7. |

### Bloco Cadastro e consulta (RF-04 a RF-07)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-04-T1 | RF-04 | Caminho feliz | ADM cria sala com nome, tipo, capacidade, localização, `restrito` | Criada; refletida em consulta em até 1s (REQUISITOS.md:27). |
| RF-04-T2 | RF-04 | Entrada inválida | Criação de sala sem `capacidade` ou com `capacidade` não numérica | HTTP 400 (RNF-04; "100% dos campos obrigatórios validados"). |
| RF-04-T3 | RF-04 | Valor-limite | `capacidade = 0` ou negativa | **[gap: não há regra explícita de capacidade mínima/positiva; inferência razoável de domínio, não um requisito escrito]** — ver §3, Q8. |
| RF-04-T4 | RF-04 | Caminho feliz | ADM inativa (soft delete) sala existente | Sala não aparece mais em RF-07 (consulta de ativos); auditoria gerada (RF-28). |
| RF-05-T1 | RF-05 | Caminho feliz | ADM cria professor com nome, identificador institucional, competências | Criado; idem RF-04 aplicado a professor. |
| RF-05-T2 | RF-05 | Conflito | Criação de professor com `identificadorInstitucional` já existente (unique) | HTTP 400/409 — violação de unicidade (MODELAGEM_DADOS.md:37, "único"). **[gap: código de status exato para violação de unicidade não especificado — 400 ou 409 — ver §3, Q9]**. |
| RF-06-T1 | RF-06 | Caminho feliz | ADM cria material com nome, tipo, quantidade, `restrito` | Criado; idem RF-04. |
| RF-06-T2 | RF-06 | Valor-limite | `quantidadeTotal = 0` | **[gap, mesma natureza de RF-04-T3]** — ver §3, Q8. |
| RF-07-T1 | RF-07 | Caminho feliz | Qualquer perfil autenticado consulta lista de salas | Retorna apenas registros `ativo = true` (REQUISITOS.md:30, "0% de registros inativados expostos"). |
| RF-07-T2 | RF-07 | Estado proibido | Consulta inclui sala inativada | Sala inativada não aparece na listagem (0% exposição). |
| RF-07-T3 | RF-07 | Entrada inválida | Usuário não autenticado tenta consultar | **[gap: RF-07 diz "qualquer perfil autenticado" — não define explicitamente se consulta exige autenticação (401 se anônimo) ou é pública]** — ver §3, Q10. |

### Bloco Pesquisa e disponibilidade (RF-08 a RF-11)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-08-T1 | RF-08 | Caminho feliz | Busca salas por tipo="laboratório", capacidade mínima=20, localização="bloco A", intervalo 10h-12h | Retorna salas que atendem todos os critérios e estão disponíveis no intervalo; resposta ≤2s p95 (RNF-01). |
| RF-08-T2 | RF-08 | Valor-limite | Capacidade mínima = capacidade exata da sala (ex.: sala com capacidade 20, busca por mínimo 20) | Sala incluída (critério é "capacidade mínima", não "capacidade maior que"). |
| RF-08-T3 | RF-08 | Entrada inválida | Busca com `capacidade mínima` negativa ou intervalo com `fim <= inicio` | HTTP 400 (RNF-04; RC-01 aplicada aos parâmetros de busca). |
| RF-09-T1 | RF-09 | Caminho feliz | Busca professores por competência="Robótica" e disponibilidade no intervalo | Retorna apenas professores com a competência exigida E disponíveis (REQUISITOS.md:37, "100% dos resultados filtrados corretamente por competência exigida"). |
| RF-09-T2 | RF-09 | Entrada inválida | Busca por competência inexistente no catálogo `Competencia` | **[gap: não definido se retorna lista vazia ou HTTP 400/404 para competência não cadastrada]** — ver §3, Q11. |
| RF-10-T1 | RF-10 | Caminho feliz | Busca materiais por tipo="projetor" e disponibilidade (quantidade livre) no intervalo | Retorna materiais com quantidade livre suficiente no intervalo. |
| RF-10-T2 | RF-10 | Valor-limite | Material com `quantidadeTotal = 3`, 2 já reservadas no intervalo, busca por 1 unidade | Deve retornar como disponível (1 unidade livre) — **[gap: RF-10/MODELAGEM_DADOS não detalham explicitamente a aritmética de quantidade parcialmente reservada por múltiplas reservas simultâneas de unidades distintas do mesmo material — a tabela `ReservaRecurso.quantidade` sugere isso, mas a regra de "soma ocupada vs. total" não está escrita literalmente]** — ver §3, Q12. |
| RF-11-T1 | RF-11 | Caminho feliz / Conflito | Recurso com reserva ativa sobreposta ou em manutenção no intervalo buscado | Excluído do resultado de busca em 100% dos casos (REQUISITOS.md:39, "0% de recursos indisponíveis retornados"). |
| RF-11-T2 | RF-11 | Caminho feliz | Recurso com reserva em estado terminal (`CANCELADA`/`REJEITADA`/`CONCLUIDA`/`NAO_COMPARECEU`) no mesmo intervalo | Incluído no resultado (não é reserva ativa) — mesmo gap de definição de "ativa" já citado (§3, Q3). |

### Bloco Criação, alteração e cancelamento de reservas (RF-12 a RF-14)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-12-T1 | RF-12 | Caminho feliz | Criar reserva com sala não restrita, início, fim, finalidade | Nasce em estado `APROVADA` (REQUISITOS.md:45, "100% das reservas sem recurso restrito nascem APROVADA"). |
| RF-12-T2 | RF-12 | Caminho feliz (fluxo de aprovação) | Criar reserva com recurso restrito (`restrito = true`) | Nasce em estado `SOLICITADA` (REQUISITOS.md:45; J2). |
| RF-12-T3 | RF-12 | Caminho feliz (misto) | Criar reserva com sala restrita + professor não restrito | Nasce `SOLICITADA` — basta **um** recurso restrito para exigir aprovação (`exigeAprovacao` derivado, MODELAGEM_DADOS.md:93, "true se algum recurso vinculado é restrito"). |
| RF-12-T4 | RF-12 | Entrada inválida | Criar reserva sem `finalidade` | HTTP 400 (`finalidade` é `not null`, MODELAGEM_DADOS.dbml:70; RNF-04). |
| RF-12-T5 | RF-12 | Entrada inválida | Criar reserva sem nenhum recurso vinculado (`ReservaRecurso` vazio) | **[gap: não há regra explícita de "reserva deve ter ao menos um recurso"; inferência razoável mas não escrita literalmente]** — ver §3, Q13. |
| RF-13-T1 | RF-13 | Caminho feliz | Solicitante altera reserva própria em estado `SOLICITADA`, mudando horário | Revalida sobreposição (RF-15) antes de confirmar; se livre, aplica a mudança. |
| RF-13-T2 | RF-13 | Caminho feliz | Solicitante altera reserva própria em estado `APROVADA` | Permitido (REQUISITOS.md:46, estados elegíveis: `SOLICITADA`/`APROVADA`). |
| RF-13-T3 | RF-13 | Estado proibido | Solicitante tenta alterar reserva em estado `EM_USO`, `CONCLUIDA`, `REJEITADA`, `CANCELADA` ou `NAO_COMPARECEU` | Rejeitado — RF-13 restringe alteração a `SOLICITADA`/`APROVADA`; **[gap: código de erro exato (403? 409? 400?) e mensagem para esse caso não especificados]** — ver §3, Q14. |
| RF-13-T4 | RF-13 | Conflito | Alteração de horário colide com outra reserva ativa do mesmo recurso | Rejeitada — HTTP 409 (revalidação de RF-15 citada explicitamente em RF-13). |
| RF-14-T1 | RF-14 | Caminho feliz | Solicitante cancela reserva própria em estado `SOLICITADA`, com motivo | Transição para `CANCELADA`, auditada (REQUISITOS.md:47). |
| RF-14-T2 | RF-14 | Caminho feliz | Cancelamento em estado `APROVADA` ou `EM_USO` | Permitido — RF-14 permite cancelamento "em qualquer estado anterior a `CONCLUÍDA`". |
| RF-14-T3 | RF-14 | Estado proibido | Tentativa de cancelar reserva já `CONCLUIDA` | Rejeitado — RF-14 exclui explicitamente `CONCLUÍDA`. |
| RF-14-T4 | RF-14 | Entrada inválida | Cancelamento sem motivo (motivo vazio/nulo) | HTTP 400, em 100% dos casos (REQUISITOS.md:47, "100% dos cancelamentos exigem motivo não vazio"). |
| RF-14-T5 | RF-14 | Estado proibido | Tentativa de cancelar reserva já `CANCELADA` ou `REJEITADA` (idempotência de estado terminal) | **[gap: não definido se é erro (409/400) ou no-op idempotente]** — ver §3, Q15. |

### Bloco Sobreposição (RF-15, RF-16) — ver RC-02 (§1.2), já cobertos lá.

### Bloco Concorrência (RF-17) — ver RC-03 (§1.3), já coberto lá.

### Bloco Aprovação de recursos restritos (RF-18 a RF-20)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-18-T1 | RF-18 | Estado proibido | Tentativa de transicionar reserva restrita diretamente de `SOLICITADA` para `EM_USO` (pulando `APROVADA`) | Bloqueado — 100% das reservas restritas passam por `APROVADA` antes de `EM_USO` (REQUISITOS.md:66). |
| RF-19-T1 | RF-19 | Caminho feliz | Responsável aprova reserva `SOLICITADA` de recurso restrito | Transição para `APROVADA`; auditoria registrada (RF-27). |
| RF-19-T2 | RF-19 | Caminho feliz | Administrador aprova reserva restrita | Permitido — RF-19 autoriza RES/ADM. |
| RF-19-T3 | RF-19 | Estado proibido | Solicitante tenta aprovar/rejeitar reserva restrita (inclusive a própria) | HTTP 403, em 100% das tentativas (REQUISITOS.md:67). |
| RF-19-T4 | RF-19 | Estado proibido (conflito de domínio) | Responsável tenta aprovar reserva já em `APROVADA`/`REJEITADA`/`CANCELADA` (fora de `SOLICITADA`) | **[gap: não definido explicitamente, mas decorre logicamente da máquina de estados J9 — transição só é válida a partir de `SOLICITADA`]** — ver §3, Q16 (consistência de transições não documentadas explicitamente para todos os pares de estado). |
| RF-20-T1 | RF-20 | Caminho feliz | Responsável rejeita reserva restrita com justificativa não vazia | Transição para `REJEITADA`; justificativa visível ao Solicitante (RF-31, RF-38). |
| RF-20-T2 | RF-20 | Entrada inválida | Rejeição sem justificativa (vazia/nula) | HTTP 400, em 100% dos casos (REQUISITOS.md:68). |

### Bloco Manutenção (RF-21 a RF-23) — ver RC-04 (§1.4), já coberto lá.

### Bloco Retirada e devolução de materiais (RF-24 a RF-26)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-24-T1 | RF-24 | Caminho feliz | Responsável registra retirada de material vinculado a reserva `EM_USO`, com data/hora e identificação de quem retirou | Registro vincula reserva + item + autor + timestamp (REQUISITOS.md:82). |
| RF-24-T2 | RF-24 | Estado proibido | Tentativa de registrar retirada de material vinculado a reserva **não** `EM_USO` (ex.: `SOLICITADA`, `APROVADA`, `CONCLUIDA`) | **[gap: RF-24 diz "vinculado a reserva EM_USO" mas não define explicitamente o código de erro para tentativa fora desse estado]** — ver §3, Q17. |
| RF-24-T3 | RF-24 | Conflito | Retirada já registrada (ciclo aberto) e nova tentativa de retirada do mesmo item antes de devolução | **[gap: não definido se permite nova retirada sem devolução prévia — comportamento de "ciclo aberto único por item" é inferido de RF-25, não explícito]** — ver §3, Q18. |
| RF-25-T1 | RF-25 | Caminho feliz | Responsável registra devolução com data/hora e condição do item (`INTEGRO`) | Encerra o ciclo de retirada aberto correspondente (REQUISITOS.md:83). |
| RF-25-T2 | RF-25 | Caminho feliz | Devolução com condição `DANIFICADO` | Aceita; condição registrada — **[gap: não há regra sobre ação decorrente de item danificado, ex.: bloqueio automático do material — não é requisito, apenas registro]**. |
| RF-25-T3 | RF-25 | Estado proibido | Tentativa de registrar devolução sem retirada prévia (ciclo não aberto) | **[gap: código de erro não especificado]** — ver §3, Q19. |
| RF-25-T4 | RF-25 | Entrada inválida | Devolução sem `condicaoDevolucao` | **[gap: RF-25 não especifica explicitamente se `condicaoDevolucao` é obrigatória, embora MODELAGEM_DADOS.md:147 a marque como nullable]** — possível ambiguidade entre "campo nullable no modelo" e "regra de negócio exige preenchimento no ato de devolução" — ver §3, Q20. |
| RF-26-T1 | RF-26, D-3, D-6 | Caminho feliz | Material retirado, reserva com `fim` no passado, sem devolução registrada, job executa | Pendência sinalizada em até 1 min após vencimento (RF-26; D-6: job a cada 30s); prazo = `Reserva.fim` (decisão D-3). |
| RF-26-T2 | RF-26 | Valor-limite | Job executa exatamente no instante `Reserva.fim` (sem ainda ter vencido) | Não deve sinalizar pendência antes do vencimento — item ainda dentro do prazo. |
| RF-26-T3 | RF-26 | Estado proibido (idempotência) | Job executa duas vezes consecutivas sobre a mesma pendência já sinalizada | Não gera sinalização duplicada (`UPDATE` condicional `WHERE pendenciaSinalizada = false`, arquitetura.md §17.2). |
| RF-26-T4 | RF-26 | Caminho feliz | Devolução registrada antes do vencimento | Pendência nunca é sinalizada para esse item. |

### Bloco Auditoria (RF-27 a RF-30)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-27-T1 | RF-27 | Caminho feliz | Qualquer transição de estado de reserva (criação, aprovação, rejeição, cancelamento, etc.) | Gera registro de auditoria correspondente, na mesma transação (ADR-002, REVISAO_ATAM.md §3); 100% de cobertura (REQUISITOS.md:90). |
| RF-28-T1 | RF-28 | Caminho feliz | Alteração de cadastro (sala, professor, material, usuário, manutenção) | Gera entrada de auditoria (REQUISITOS.md:91). |
| RF-28-T2 | RF-28 | Conflito (nomenclatura, ver O3) | Rejeição de reserva por sobreposição (checagem RF-15, não decisão humana) | **[gap/inconsistência identificada em REVISAO_ATAM.md §1 "Dados/ML" O3]**: não há definição formal do valor de `acao` a ser gravado para esse evento (ex.: `REJEICAO_SOBREPOSICAO` usado em arquitetura.md §17.3 não consta na lista de exemplos de MODELAGEM_DADOS.md:161, que lista apenas "CRIACAO, APROVACAO, REJEICAO, CANCELAMENTO, ATUALIZACAO, INATIVACAO") — necessário para RF-35 funcionar corretamente. Ver §3, Q21. |
| RF-29-T1 | RF-29 | Estado proibido | Tentativa de `PUT`/`PATCH`/`DELETE` sobre o recurso de auditoria | Endpoint inexistente — verificado por ausência, não por erro de negócio (REQUISITOS.md:92, "Ausência verificada de endpoint"). |
| RF-30-T1 | RF-30 | Estado proibido | Tentativa de `DELETE` sobre reserva em qualquer estado diferente de `SOLICITADA` | HTTP 403/405, em 100% dos casos (REQUISITOS.md:93). |
| RF-30-T2 | RF-30 | Caminho feliz (fora da regra, mas relevante) | `DELETE` de reserva ainda em `SOLICITADA` | **[gap: RF-30 proíbe exclusão de reservas "iniciadas" (fora de SOLICITADA); não fica claro se DELETE de uma reserva em SOLICITADA é permitido ou se a única forma de "encerrar" é cancelamento (RF-14), já que MODELAGEM_DADOS.md:212-213 afirma "Reserva nunca é excluída fisicamente... a tabela não possui operação de DELETE exposta"]** — há tensão entre RF-30 (implica DELETE pode existir para SOLICITADA) e a decisão de modelagem (nenhum DELETE exposto, ponto final). Ver §3, Q22 — **inconsistência interna entre REQUISITOS.md e MODELAGEM_DADOS.md**. |

### Bloco Notificações (RF-31 a RF-33) — ADR-003 (REVISAO_ATAM.md §3)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-31-T1 | RF-31 | Caminho feliz | Mudança de estado da própria reserva do Solicitante (qualquer transição) | Notificação disparada em até 5s após a transição persistida (REQUISITOS.md:99). |
| RF-32-T1 | RF-32 | Caminho feliz | Nova solicitação restrita criada (`SOLICITADA` por recurso restrito) | Responsável competente notificado em até 5s (REQUISITOS.md:100). |
| RF-32-T2 | RF-32 | Entrada inválida/gap | Recurso restrito sem Responsável associado cadastrado | **[gap: não há, em MODELAGEM_DADOS.md, vínculo explícito entre `Sala`/`Material` restrito e um `Usuario` perfil RESPONSAVEL "competente" — o modelo de dados não define como determinar "o Responsável competente" para um recurso]** — ver §3, Q23 (inconsistência adicional entre RF-32 e a modelagem de dados). |
| RF-33-T1 | RF-33, ADR-003 | Conflito/Falha simulada | Endpoint de notificação externa indisponível (WireMock configurado para falhar) durante uma transição de estado | Transição persiste normalmente; notificação registrada como `FALHA` em `NotificacaoEnviada`, sem reverter a transação de negócio (RF-33, critério "100% das transições persistem mesmo com endpoint indisponível"). |
| RF-33-T2 | RF-33 | Caminho feliz | Endpoint de notificação disponível | Transição persiste; `NotificacaoEnviada.status = ENVIADA`. |

### Bloco Relatórios (RF-34 a RF-36)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-34-T1 | RF-34 | Caminho feliz | Gerar relatório de utilização para intervalo de datas com reservas `CONCLUIDA`/`EM_USO` conhecidas | Relatório reflete 100% dessas reservas, validado contra dados conhecidos (REQUISITOS.md:107). |
| RF-34-T2 | RF-34 | Valor-limite | Intervalo de datas sem nenhuma reserva no período | Relatório retorna zerado (sem erro) — **[gap: comportamento exato não detalhado, mas inferido como caminho feliz trivial]**. |
| RF-34-T3 | RF-34 | Entrada inválida | Intervalo de datas com `fim <= inicio` no filtro do relatório | HTTP 400 (RNF-04, RC-01 aplicada a parâmetros de relatório). |
| RF-35-T1 | RF-35 | Caminho feliz | Contagem de rejeições por sobreposição/dupla reserva no período | Bate exatamente com o número de respostas HTTP 409 registradas em auditoria no período (REQUISITOS.md:108) — **depende da resolução do gap RF-28-T2/Q21 (nomenclatura de `acao`)**. |
| RF-36-T1 | RF-36 | Estado proibido | Solicitante tenta acessar qualquer endpoint de relatório | HTTP 403, em 100% das tentativas (REQUISITOS.md:109). |
| RF-36-T2 | RF-36 | Caminho feliz | Responsável ou Administrador acessam relatório | Permitido. |

### Bloco Interface e erros (RF-37, RF-38) — não testável via testes de API/unitário puros

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-37-T1 | RF-37 | Caminho feliz | Renderização em viewport 360px, 768px, 1920px | Sem quebra de layout (REQUISITOS.md:115) — **teste manual/visual ou E2E com ferramenta de viewport, não unitário de backend**. |
| RF-38-T1 | RF-38 | Entrada inválida / caminho de erro | Qualquer resposta HTTP 4xx de regra de negócio (ex.: RC-02-T3, RF-14-T4) | Mensagem em linguagem natural, sem stacktrace/nome de classe técnico (REQUISITOS.md:116; RNF-06). |

### Bloco Documentação (RF-39)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RF-39-T1 | RF-39 | Caminho feliz | Geração da especificação OpenAPI | 100% dos endpoints expostos aparecem na especificação (REQUISITOS.md:122) — **teste de contrato/geração de doc, não regra de negócio em si**. |

---

## 3. Requisitos não funcionais relevantes a testes automatizados (RNF)

| Caso | Origem | Categoria | Entrada | Resultado esperado |
|---|---|---|---|---|
| RNF-04-T1 | RNF-04 | Entrada inválida | Qualquer campo obrigatório ausente/mal formatado em qualquer endpoint de escrita | HTTP 400 (REQUISITOS.md:136). |
| RNF-05-T1 | RNF-05 | Estado proibido | Inspeção de `senha_hash` no banco após cadastro de usuário | Nunca texto plano — hash BCrypt (REQUISITOS.md:137). |
| RNF-06-T1 | RNF-06 | Estado proibido | Qualquer resposta de erro observada (4xx/5xx) | Sem stacktrace nem nome de classe interna (REQUISITOS.md:138). |
| RNF-08-T1 | RNF-08 | Conflito sob falha parcial | Ver RC-03-T4 | Idêntico — mesma regra, origem explícita em RNF-08. |
| RNF-09-T1 | RNF-09 | Estado proibido | Verificação de integridade referencial (reserva ↔ recurso ↔ auditoria) | 0 registros órfãos detectados (REQUISITOS.md:141). |

> RNF-01, RNF-02, RNF-03 (desempenho/JMeter), RNF-07 (SonarCloud), RNF-10 a RNF-14 (cobertura,
> RTM, CI, TDD, acessibilidade) e RNF-15/RNF-16 (arquitetura/isolamento de integrações) são
> metas de processo/infraestrutura de teste, não regras de negócio unitárias — não geram casos
> de teste de regra de negócio nesta tabela, mas devem constar no plano de testes de carga
> (JMeter) e na suíte de integração/E2E separadamente.

---

## 4. Máquina de estados da Reserva (J9) — matriz de transições válidas/proibidas

**Origem:** enunciado.md:56-58 (estados principais e alternativos); JORNADAS.md:144-158 (J9,
diagrama de transições); MODELAGEM_DADOS.md:92 (enum `estado`).

```
SOLICITADA --(aprovação, RF-19)--> APROVADA --(início do uso)--> EM_USO --(fim do uso)--> CONCLUIDA
    |--(rejeição, RF-20)--> REJEITADA
    |--(cancelamento, RF-14)--> CANCELADA
APROVADA --(cancelamento, RF-14)--> CANCELADA
EM_USO --(solicitante não aparece)--> NAO_COMPARECEU
```

| Caso | Categoria | Transição testada | Resultado esperado |
|---|---|---|---|
| EST-T1 | Caminho feliz | `SOLICITADA → APROVADA` (recurso restrito, aprovação) | Permitida; auditoria gerada; notificação disparada. |
| EST-T2 | Caminho feliz | `APROVADA → EM_USO` | Permitida — **[gap: o gatilho exato de "início do uso" não está definido — é automático por horário (job) ou manual (ação de usuário)? Nenhum artefato (REQUISITOS, JORNADAS, arquitetura) define isso explicitamente]** — ver §3 abaixo (seção de lacunas), Q24. |
| EST-T3 | Caminho feliz | `EM_USO → CONCLUIDA` | Permitida — mesmo gap de gatilho (Q24). |
| EST-T4 | Caminho de erro | `SOLICITADA → REJEITADA` | Permitida, exige justificativa (RF-20). |
| EST-T5 | Caminho de erro | `SOLICITADA → CANCELADA` | Permitida (RF-14), exige motivo. |
| EST-T6 | Caminho de erro | `APROVADA → CANCELADA` | Permitida (RF-14), exige motivo. |
| EST-T7 | Caminho de erro | `EM_USO → NAO_COMPARECEU` | Permitida — **[gap: gatilho e critério de "não compareceu" não definidos em nenhum artefato — automático após X minutos de atraso? Registrado manualmente por quem?]** — ver §3, Q25. |
| EST-T8 | Estado proibido | `SOLICITADA → EM_USO` (pulando `APROVADA`), quando reserva tem recurso restrito | Bloqueado (RF-18). |
| EST-T9 | Estado proibido | `SOLICITADA → EM_USO` (pulando `APROVADA`), quando reserva **não** tem recurso restrito | **[gap: RF-18 fala especificamente de recursos restritos; não é claro se reserva sem restrição também passa obrigatoriamente por `APROVADA` (ela já "nasce" `APROVADA` segundo RF-12, então a transição `SOLICITADA→EM_USO` nem ocorreria nesse caso) — aparenta ser não-aplicável, mas vale confirmar]** — ver §3, Q24 (mesmo tema do gatilho de transição). |
| EST-T10 | Estado proibido | `REJEITADA → *` (qualquer transição a partir de estado terminal) | Bloqueada — estados terminais (`REJEITADA`, `CANCELADA`, `CONCLUIDA`, `NAO_COMPARECEU`) não têm saída no diagrama J9. **[gap: não há confirmação textual explícita de que esses 4 estados são "terminais" e que qualquer tentativa de transição a partir deles deve ser rejeitada com erro — é inferido do diagrama, não afirmado em prosa]** — ver §3, Q16. |
| EST-T11 | Estado proibido | `CONCLUIDA → *` | Idem EST-T10. |
| EST-T12 | Estado proibido | `CANCELADA → *` | Idem EST-T10. |
| EST-T13 | Estado proibido | `NAO_COMPARECEU → *` | Idem EST-T10. |

---

## 5. Ambiguidades, lacunas e perguntas em aberto (não assumidas como regra)

Nenhum item abaixo foi tratado como regra de negócio nas tabelas acima quando marcado **[gap]**.
Perguntas já levantadas e **resolvidas** por decisão do responsável do projeto (`arquitetura.md
§7.1`, D-1 a D-8) não são repetidas aqui — apenas o que permanece sem resposta.

| ID | Pergunta | Onde aparece / por que importa para o teste | Status |
|---|---|---|---|
| Q1 | Existe granularidade mínima de intervalo de reserva (ex.: múltiplos de 30 min)? | RC-01-T2 — afeta se um teste de "1 minuto de duração" deve passar ou falhar. | Em aberto — nenhum artefato define. |
| Q2 | A checagem de sobreposição (RC-02) e de manutenção (RC-04) trata intervalos como fechados `[inicio,fim]` ou semiabertos `[inicio,fim)`? Ou seja, dois intervalos contíguos (`fim` de um == `inicio` do outro) contam como sobreposição? | RC-02-T2, RC-04-T3 — resultado do teste de borda muda completamente conforme a resposta. `arquitetura.md` menciona `tsrange` (PostgreSQL), cuja notação padrão é `[)`, mas isso não é afirmado como decisão de negócio, só como detalhe de implementação sugerido. | Em aberto — candidato a confirmação com o responsável antes de escrever o teste de borda. |
| Q3 | Quais estados de `Reserva` contam como "ativa" para fins de bloqueio de sobreposição (RF-15) e de exclusão de busca (RF-11)? | RC-02-T9, RC-04-T6, RF-11-T2, EST-T10-13 — usado em múltiplos casos de teste. Inferência atual: `SOLICITADA`, `APROVADA`, `EM_USO` são "ativas"; `REJEITADA`, `CANCELADA`, `CONCLUIDA`, `NAO_COMPARECEU` não são. Essa inferência é razoável mas **não está escrita literalmente em nenhum artefato**. | Em aberto — recomenda-se registrar como decisão formal antes de implementar RC-02/RF-11. |
| Q4 | Reenvio idêntico do mesmo solicitante (retry de rede) para o mesmo recurso/intervalo deve ser bloqueado como duplicata, exigindo `Idempotency-Key`? | RC-03-T5 — já identificado como objeção O1 não fechada em `REVISAO_ATAM.md` §1 e pergunta 2 de `REVISAO_ATAM.md` §8. | Em aberto — pergunta já formalmente registrada pelo painel ATAM, aguardando decisão do responsável. |
| Q5 | A regra "término posterior ao início" (RC-01) se aplica formalmente a `Manutencao`, ou apenas a `Reserva` (único `CHECK` citado em MODELAGEM_DADOS.md:99)? | RC-04-T7 | Em aberto — inferência razoável, não confirmada. |
| Q6 | A resposta HTTP 401 de RF-01 deve ser idêntica (corpo/mensagem) tanto para "senha errada" quanto para "usuário inexistente"? | RF-01-T3 | Em aberto — RF-01 só diz "sem indicar qual campo está errado", o que é compatível com, mas não exige explicitamente, uma resposta unificada entre os dois casos. |
| Q7 | Solicitante pode **consultar** (GET, somente leitura) reserva de outro solicitante, ou só está proibido de alterar/cancelar? | RF-03-T3 | Em aberto — RF-03 e RF-07 não esclarecem o escopo de leitura de reservas de terceiros. |
| Q8 | Existe regra de capacidade/quantidade mínima (> 0) para Sala/Material? | RF-04-T3, RF-06-T2 | Em aberto — nenhum artefato define um mínimo explícito além do tipo de dado ser `Integer`/`int`. |
| Q9 | Qual o código HTTP esperado para violação de unicidade (e-mail de usuário, identificador institucional de professor, nome de competência)? 400 ou 409? | RF-05-T2 | Em aberto — REQUISITOS.md não distingue "erro de validação de formato" de "conflito de unicidade" em termos de código HTTP. |
| Q10 | A consulta somente-leitura de RF-07 exige autenticação (401 se anônimo) ou é pública? | RF-07-T3 | Em aberto — RF-07 diz "qualquer perfil autenticado", o que sugere exigir autenticação, mas não afirma o comportamento para requisição anônima. |
| Q11 | Busca de professor por competência inexistente no catálogo retorna lista vazia ou erro (400/404)? | RF-09-T2 | Em aberto. |
| Q12 | Como exatamente a "quantidade livre" de um Material é calculada quando há múltiplas reservas parciais do mesmo item no mesmo intervalo? | RF-10-T2 | Em aberto — a tabela `ReservaRecurso.quantidade` sugere o mecanismo, mas a regra de soma/validação não está descrita em prosa normativa. |
| Q13 | Uma reserva pode ser criada sem nenhum `ReservaRecurso` vinculado? | RF-12-T5 | Em aberto — provavelmente não, mas não há regra escrita. |
| Q14 | Qual o código de erro exato para tentativa de alteração de reserva fora dos estados `SOLICITADA`/`APROVADA` (RF-13)? | RF-13-T3 | Em aberto. |
| Q15 | Cancelar uma reserva já `CANCELADA`/`REJEITADA` é erro ou operação idempotente (no-op)? | RF-14-T5 | Em aberto. |
| Q16 | Toda tentativa de transição inválida a partir de um estado terminal (`REJEITADA`, `CANCELADA`, `CONCLUIDA`, `NAO_COMPARECEU`) deve retornar o mesmo código de erro? Qual? | RF-19-T4, EST-T10 a EST-T13 | Em aberto — a máquina de estados (J9) está descrita como diagrama, mas as regras de erro para transições fora do diagrama não são enumeradas em prosa. |
| Q17 | Qual o código de erro para tentativa de registrar retirada de material de reserva fora do estado `EM_USO`? | RF-24-T2 | Em aberto. |
| Q18 | É permitido abrir uma nova retirada do mesmo item antes de fechar (devolver) a retirada anterior? | RF-24-T3 | Em aberto. |
| Q19 | Qual o código de erro para devolução sem retirada prévia registrada? | RF-25-T3 | Em aberto. |
| Q20 | `condicaoDevolucao` é obrigatória no ato de devolução, apesar de `nullable` no modelo de dados? | RF-25-T4 | Em aberto — possível tensão entre MODELAGEM_DADOS.md (campo nullable, pensado para cobrir "ainda não devolvido") e a regra de negócio de RF-25 (registrar devolução "incluindo condição do item", JORNADAS.md:108-109, sugere que é esperada no ato). |
| Q21 | Qual valor exato de `acao` em `AuditoriaEvento` deve ser gravado para uma rejeição automática por sobreposição (usado por RF-35)? | RF-28-T2, RF-35-T1 | **Já identificado formalmente** como objeção O3 em `REVISAO_ATAM.md` §1 ("Dados/ML") e pergunta 1 de `REVISAO_ATAM.md` §8 — inconsistência confirmada entre `arquitetura.md §17.3` (usa `REJEICAO_SOBREPOSICAO`) e `MODELAGEM_DADOS.md:161` (não lista esse valor). Em aberto. |
| Q22 | RF-30 (REQUISITOS.md:93) menciona "tentativas de DELETE sobre reserva iniciada retornam HTTP 403/405", o que implica a existência de um endpoint DELETE (ainda que bloqueado para a maioria dos estados); já MODELAGEM_DADOS.md:212-213 afirma que "a tabela não possui operação de DELETE exposta" para **nenhum** estado. Existe endpoint DELETE para reserva em `SOLICITADA`, ou nenhum DELETE é exposto em hipótese alguma (uso exclusivo de RF-14/cancelamento)? | RF-30-T2 | **Inconsistência interna identificada nesta análise** entre REQUISITOS.md e MODELAGEM_DADOS.md — recomenda-se esclarecer com o responsável antes de implementar o controller de reservas. |
| Q23 | Como o sistema determina qual Responsável é "competente" para um recurso restrito, para fins de notificação (RF-32)? | RF-32-T2 | Em aberto — nenhuma tabela/FK no modelo de dados (MODELAGEM_DADOS.md) vincula `Sala`/`Material` restrito a um `Usuario` perfil RESPONSAVEL responsável por aquele recurso especificamente; a persona Administrador menciona "definir sobre quais recursos ele tem autoridade" (administrador.md:59) como cenário de uso, mas isso não está modelado como entidade/relacionamento em MODELAGEM_DADOS.md. |
| Q24 | O gatilho das transições `APROVADA → EM_USO` e `EM_USO → CONCLUIDA` é automático (por horário, via job) ou manual (ação de um usuário)? Quem pode acioná-la, se manual? | EST-T2, EST-T3, EST-T9 | Em aberto — nenhum artefato (enunciado, REQUISITOS, JORNADAS, arquitetura) especifica o mecanismo; JORNADAS.md:105 apenas diz "Reserva de material transiciona para EM_USO (ex.: no início do horário agendado)", usando "ex.:" (exemplo, não norma). |
| Q25 | Qual o critério e o gatilho para a transição `EM_USO → NAO_COMPARECEU`? Há um prazo de tolerância? Quem registra? | EST-T7 | Em aberto — citado apenas como estado alternativo existente (enunciado.md:58), sem regra de acionamento. |

### Lacunas adicionais já formalmente registradas em artefatos existentes (referenciadas, não repetidas em detalhe)

As perguntas abaixo já constam, com maior detalhamento, em `REVISAO_ATAM.md §8` e
`arquitetura.md §7`/§19 — citadas aqui apenas para rastreabilidade com os testes afetados, sem
duplicar o texto integral:

- Necessidade de healthcheck/restart documentado para a instância única (impacta a
  confiabilidade do job de RF-26, mas não gera caso de teste de regra de negócio unitário) —
  `REVISAO_ATAM.md §1 "Operações/SRE" O7`.
- Direitos de titular de dados pessoais (LGPD Art. 18) além dos controles técnicos já cobertos
  por RNF-05/RF-01-03/RNF-06 — `REVISAO_ATAM.md §1 "Segurança/LGPD" O5`; não gera caso de teste
  de regra de negócio nesta fase, pois D-8 já define o escopo mínimo como suficiente.

---

## 6. Observações para a escolha da funcionalidade a desenvolver via TDD (RNF-13)

Este documento não seleciona qual funcionalidade será desenvolvida com evidência de TDD — essa é
uma decisão de implementação fora do escopo deste inventário. Candidatas notáveis, por terem
regras de negócio objetivas e sem gaps bloqueantes (ou com gaps isoláveis), incluem:

- **RC-01** (término posterior ao início) — regra simples, sem gap bloqueante (Q1 é um refinamento
  de granularidade, não impede o ciclo red-green básico).
- **RF-14** (cancelamento com motivo obrigatório) — regra objetiva e auto-contida, único gap (Q15)
  isolável.
- **RF-20** (rejeição com justificativa obrigatória) — idem RF-14, sem gaps bloqueantes.

RC-02/RC-03 (sobreposição e concorrência) são os requisitos mais arquiteturalmente significativos
(ver `arquitetura.md §3`), mas dependem de infraestrutura ainda não implementada (Testcontainers,
constraint de banco) e de gaps não resolvidos (Q2, Q3, Q4) — recomenda-se resolver essas perguntas
com o responsável antes de usá-los como a funcionalidade de evidência de TDD.

---

## 7. Matriz regra → caso → teste → status (fase GREEN executada)

> Executado em 2026-10-27. Suíte: `mvn verify` (wrapper `./mvnw` ausente no repositório; comando
> equivalente usado com Maven instalado localmente), **123 testes no total, 123 verdes, 0
> falhas**. Toda a lógica de negócio foi implementada em `src/main/java/com/edualoca/domain/**`,
> com um repositório em memória compartilhado (`Banco`, pacote `domain.comum`) simulando a
> persistência única ainda não disponível (sem JPA nesta fase), e uma extensão JUnit
> (`BancoResetExtension`, registrada via `META-INF/services`) que limpa esse estado antes de cada
> teste para isolamento.
>
> **Correção de 12 testes com fixture ausente (2026-10-27, autorizada pelo responsável):** uma
> primeira rodada da fase GREEN havia deixado 12 testes em RED porque pares de métodos de teste
> chamavam o mesmo método de serviço com um `UUID.randomUUID()` nunca antes criado, sem nenhum
> `arrange` que materializasse o estado esperado (ex.: reserva `EM_USO`, reserva já `APROVADA`),
> e ainda assim esperavam resultados opostos — divergência estrutural, não de regra de negócio.
> Consultado, o responsável autorizou ajustar os testes para criarem/levarem a reserva (ou o
> registro de retirada) ao estado necessário antes de invocar o método, via `ReservaService`/
> `RetiradaMaterialService` reais, preservando a asserção e o resultado esperado de cada teste.
> Isso foi aplicado aos 12 casos (ver §8 "Correções aplicadas" abaixo). Adicionalmente, para
> `NotificacaoService.notificarTransicaoDeEstado` (RF-33-T1/T2), que não tinha nenhum parâmetro
> capaz de diferenciar endpoint disponível de indisponível, foi introduzido um pequeno ponto de
> injeção (`NotificacaoService.NotificadorExterno`, construtor alternativo) permitindo ao teste
> RF-33-T1 simular indisponibilidade explicitamente — sem alterar a asserção/resultado esperado
> de nenhum teste.
>
> Limitação mantida da fase RED: RC-03 (concorrência) e RF-33 (falha de endpoint externo) rodam
> como testes unitários em memória (sem Testcontainers/WireMock, fora do escopo desta fase).

| Regra | Caso | Classe de teste | Método de teste | Status |
|---|---|---|---|---|
| RC-01 | RC-01-T1 | RegrasCriticasTest | rc01T1_inicioAnteriorAoFim_devePermitirCriacao | GREEN |
| RC-01 | RC-01-T2 | RegrasCriticasTest | rc01T2_menorIntervaloValido_devePermitirCriacao | GREEN (assunção Q1) |
| RC-01 | RC-01-T3 | RegrasCriticasTest | rc01T3_fimIgualInicio_deveSerRejeitadoComBadRequest | GREEN |
| RC-01 | RC-01-T4 | RegrasCriticasTest | rc01T4_fimAnteriorAoInicio_deveSerRejeitadoComBadRequest | GREEN |
| RC-01 | RC-01-T5 | RegrasCriticasTest | rc01T5_inicioOuFimNulos_deveSerRejeitadoComBadRequest | GREEN |
| RC-01 | RC-01-T6 | RegrasCriticasTest | rc01T6_alteracaoComFimMenorOuIgualInicio_deveSerRejeitada | GREEN |
| RC-02 | RC-02-T1 | RegrasCriticasTest | rc02T1_reservasDisjuntasMesmaSala_devemSerAmbasAceitas | GREEN |
| RC-02 | RC-02-T2 | RegrasCriticasTest | rc02T2_reservasContiguas_devemSerAceitasSemSobreposicao | GREEN (assunção Q2) |
| RC-02 | RC-02-T3 | RegrasCriticasTest | rc02T3_sobreposicaoParcial_deveSerRejeitadaComConflito | GREEN |
| RC-02 | RC-02-T4 | RegrasCriticasTest | rc02T4_reservaEnglobaOutra_deveSerRejeitadaComConflito | GREEN |
| RC-02 | RC-02-T5 | RegrasCriticasTest | rc02T5_intervalosIdenticos_devemSerRejeitadosComConflito | GREEN |
| RC-02 | RC-02-T6 | RegrasCriticasTest | rc02T6_sobreposicaoAgendaProfessorRecursoAcessorio_deveSerRejeitada | GREEN |
| RC-02 | RC-02-T7 | RegrasCriticasTest | rc02T7_multiRecursoSemConflito_deveSerAceita | GREEN |
| RC-02 | RC-02-T8 | RegrasCriticasTest | rc02T8_apenasUmRecursoEmConflito_deveSerRejeitada | GREEN |
| RC-02 | RC-02-T9 | RegrasCriticasTest | rc02T9_reservaCanceladaNaoBloqueiaNovaReserva | GREEN (assunção Q3) |
| RC-02 | RC-02-T10 | RegrasCriticasTest | rc02T10_salaEmManutencao_deveRejeitarReservaComConflito | GREEN |
| RC-03 | RC-03-T1 | RegrasCriticasTest | rc03T1_unicaRequisicaoSemDisputa_deveSerAceita | GREEN |
| RC-03 | RC-03-T2 | RegrasCriticasTest | rc03T2_duasRequisicoesConcorrentes_apenasUmaDeveSerAceita | GREEN (limitação: sem Testcontainers, simulado em memória) |
| RC-03 | RC-03-T3 | RegrasCriticasTest | rc03T3_cincoRequisicoesConcorrentes_apenasUmaDeveSerAceita | GREEN (limitação: sem Testcontainers, simulado em memória) |
| RC-03 | RC-03-T4 | RegrasCriticasTest | rc03T4_falhaParcialDuranteCriacao_naoDeveDeixarEstadoInconsistente | GREEN (limitação: sem Testcontainers, simulado em memória) |
| **RC-03** | **RC-03-T5** | — | — | **SEM TESTE — gap bloqueante Q4 (reenvio idêntico do mesmo solicitante); o próprio plano instrui a não escrever este teste até decisão do responsável (objeção O1)** |
| RC-04 | RC-04-T1 | RegrasCriticasTest | rc04T1_registroDeManutencao_devePersistirPeriodo | GREEN |
| RC-04 | RC-04-T2 | RegrasCriticasTest | rc04T2_reservaDentroDoPeriodoDeManutencao_deveSerRejeitada | GREEN |
| RC-04 | RC-04-T3 | RegrasCriticasTest | rc04T3_reservaNoLimiteDaManutencao_deveSerAceita | GREEN (assunção Q2) |
| RC-04 | RC-04-T4 | RegrasCriticasTest | rc04T4_manutencaoSobrepoeReservaAprovada_exigeConfirmacaoOuRecusa | GREEN |
| RC-04 | RC-04-T5 | RegrasCriticasTest | rc04T5_manutencaoSobrepoeReservaEmUso_exigeConfirmacaoOuRecusa | GREEN |
| RC-04 | RC-04-T6 | RegrasCriticasTest | rc04T6_manutencaoSobreReservaTerminal_naoExigeConfirmacao | GREEN |
| RC-04 | RC-04-T7 | RegrasCriticasTest | rc04T7_manutencaoComFimMenorOuIgualInicio_deveSerRejeitada | GREEN (assunção Q5) |
| RC-04 | RC-04-T8 | RegrasCriticasTest | rc04T8_manutencaoSemMotivo_deveSerRejeitada | GREEN |
| RF-01 | RF-01-T1 | AutenticacaoAutorizacaoTest | rf01T1_credenciaisValidas_deveAutenticar | GREEN |
| RF-01 | RF-01-T2 | AutenticacaoAutorizacaoTest | rf01T2_senhaIncorreta_deveRetornar401 | GREEN |
| RF-01 | RF-01-T3 | AutenticacaoAutorizacaoTest | rf01T3_emailInexistente_deveRetornar401Generico | GREEN (assunção Q6) |
| RF-02 | RF-02-T1 | AutenticacaoAutorizacaoTest | rf02T1_administrador_deveSerAutorizado | GREEN |
| RF-02 | RF-02-T2 | AutenticacaoAutorizacaoTest | rf02T2_solicitanteEmEndpointAdmOnly_deveRetornar403 | GREEN |
| RF-02 | RF-02-T3 | AutenticacaoAutorizacaoTest | rf02T3_responsavelEmCrudDeUsuarios_deveRetornar403 | GREEN |
| RF-03 | RF-03-T1 | AutenticacaoAutorizacaoTest | rf03T1_solicitanteAlteraPropriaReserva_devePermitir | GREEN |
| RF-03 | RF-03-T2 | AutenticacaoAutorizacaoTest | rf03T2_solicitanteAlterandoReservaDeOutroSolicitante_deveRetornar403 | GREEN |
| RF-03 | RF-03-T3 | AutenticacaoAutorizacaoTest | rf03T3_solicitanteConsultaReservaDeOutro_deveRetornar403 | GREEN (assunção Q7) |
| RF-04 | RF-04-T1 | CadastroConsultaTest | rf04T1_criacaoDeSalaComCamposValidos_deveCriar | GREEN |
| RF-04 | RF-04-T2 | CadastroConsultaTest | rf04T2_criacaoDeSalaSemCapacidade_deveSerRejeitada | GREEN |
| RF-04 | RF-04-T3 | CadastroConsultaTest | rf04T3_capacidadeZeroOuNegativa_deveSerRejeitada | GREEN (assunção Q8) |
| RF-04 | RF-04-T4 | CadastroConsultaTest | rf04T4_inativacaoDeSala_naoDeveAparecerNaListagem | GREEN |
| RF-05 | RF-05-T1 | CadastroConsultaTest | rf05T1_criacaoDeProfessorComCamposValidos_deveCriar | GREEN |
| RF-05 | RF-05-T2 | CadastroConsultaTest | rf05T2_identificadorInstitucionalDuplicado_deveSerRejeitadoComConflito | GREEN (assunção Q9) |
| RF-06 | RF-06-T1 | CadastroConsultaTest | rf06T1_criacaoDeMaterialComCamposValidos_deveCriar | GREEN |
| RF-06 | RF-06-T2 | CadastroConsultaTest | rf06T2_quantidadeTotalZero_deveSerRejeitada | GREEN (assunção Q8) |
| RF-07 | RF-07-T1 | CadastroConsultaTest | rf07T1_consultaDeSalas_deveRetornarApenasAtivas | GREEN |
| RF-07 | RF-07-T2 | CadastroConsultaTest | rf07T2_salaInativada_naoDeveAparecerNaListagem | GREEN |
| RF-07 | RF-07-T3 | CadastroConsultaTest | rf07T3_usuarioNaoAutenticado_deveReceber401 | GREEN (assunção Q10) |
| RF-08 | RF-08-T1 | PesquisaDisponibilidadeTest | rf08T1_buscaComCriteriosValidos_deveRetornarSalasCompativeis | GREEN |
| RF-08 | RF-08-T2 | PesquisaDisponibilidadeTest | rf08T2_capacidadeMinimaIgualACapacidadeDaSala_deveIncluirSala | GREEN |
| RF-08 | RF-08-T3 | PesquisaDisponibilidadeTest | rf08T3_intervaloInvalidoNaBusca_deveSerRejeitado | GREEN |
| RF-09 | RF-09-T1 | PesquisaDisponibilidadeTest | rf09T1_buscaPorCompetenciaEDisponibilidade_deveFiltrarCorretamente | GREEN |
| RF-09 | RF-09-T2 | PesquisaDisponibilidadeTest | rf09T2_competenciaInexistente_deveRetornarListaVazia | GREEN (assunção Q11) |
| RF-10 | RF-10-T1 | PesquisaDisponibilidadeTest | rf10T1_buscaDeMateriaisComQuantidadeLivre_deveRetornarDisponiveis | GREEN |
| RF-10 | RF-10-T2 | PesquisaDisponibilidadeTest | rf10T2_materialComUmaUnidadeLivre_deveAparecerDisponivel | GREEN (assunção Q12) |
| RF-11 | RF-11-T1 | PesquisaDisponibilidadeTest | rf11T1_recursoComReservaAtivaOuManutencao_deveSerExcluido | GREEN |
| RF-11 | RF-11-T2 | PesquisaDisponibilidadeTest | rf11T2_recursoComReservaTerminal_deveSerIncluido | GREEN (assunção Q3) |
| RF-12 | RF-12-T1 | ReservaCicloVidaTest | rf12T1_reservaComSalaNaoRestrita_deveNascerAprovada | GREEN |
| RF-12 | RF-12-T2 | ReservaCicloVidaTest | rf12T2_reservaComRecursoRestrito_deveNascerSolicitada | GREEN |
| RF-12 | RF-12-T3 | ReservaCicloVidaTest | rf12T3_reservaMista_deveNascerSolicitadaPorUmRecursoRestrito | GREEN |
| RF-12 | RF-12-T4 | ReservaCicloVidaTest | rf12T4_reservaSemFinalidade_deveSerRejeitada | GREEN |
| RF-12 | RF-12-T5 | ReservaCicloVidaTest | rf12T5_reservaSemRecursoVinculado_deveSerRejeitada | GREEN (assunção Q13) |
| RF-13 | RF-13-T1 | ReservaCicloVidaTest | rf13T1_alterarReservaSolicitadaComHorarioLivre_devePermitir | GREEN |
| RF-13 | RF-13-T2 | ReservaCicloVidaTest | rf13T2_alterarReservaAprovada_devePermitir | GREEN |
| RF-13 | RF-13-T3 | ReservaCicloVidaTest | rf13T3_alterarReservaEmUso_deveSerRejeitada | GREEN (assunção Q14) |
| RF-13 | RF-13-T4 | ReservaCicloVidaTest | rf13T4_alteracaoComColisaoDeHorario_deveSerRejeitada | GREEN |
| RF-14 | RF-14-T1 | ReservaCicloVidaTest | rf14T1_cancelamentoComMotivo_deveTransicionarParaCancelada | GREEN |
| RF-14 | RF-14-T2 | ReservaCicloVidaTest | rf14T2_cancelamentoEmAprovadaOuEmUso_devePermitir | GREEN |
| RF-14 | RF-14-T3 | ReservaCicloVidaTest | rf14T3_cancelamentoDeReservaConcluida_deveSerRejeitado | GREEN |
| RF-14 | RF-14-T4 | ReservaCicloVidaTest | rf14T4_cancelamentoSemMotivo_deveSerRejeitado | GREEN |
| RF-14 | RF-14-T5 | ReservaCicloVidaTest | rf14T5_cancelamentoDeReservaJaCancelada_deveSerRejeitado | GREEN (assunção Q15) |
| RF-15, RF-16 | — | — | — | Ver RC-02 (§1.2), já coberto acima — sem casos adicionais nesta seção. |
| RF-17 | — | — | — | Ver RC-03 (§1.3), já coberto acima — sem casos adicionais nesta seção. |
| RF-18 | RF-18-T1 | AprovacaoRecursosRestritosTest | rf18T1_transicaoDiretaSolicitadaParaEmUso_deveSerBloqueada | GREEN |
| RF-19 | RF-19-T1 | AprovacaoRecursosRestritosTest | rf19T1_responsavelAprovaReservaRestrita_deveTransicionarParaAprovada | GREEN |
| RF-19 | RF-19-T2 | AprovacaoRecursosRestritosTest | rf19T2_administradorAprovaReservaRestrita_devePermitir | GREEN |
| RF-19 | RF-19-T3 | AprovacaoRecursosRestritosTest | rf19T3_solicitanteTentaAprovar_deveRetornar403 | GREEN |
| RF-19 | RF-19-T4 | AprovacaoRecursosRestritosTest | rf19T4_aprovarReservaForaDeSolicitada_deveSerRejeitado | GREEN (assunção Q16) |
| RF-20 | RF-20-T1 | AprovacaoRecursosRestritosTest | rf20T1_rejeicaoComJustificativa_deveTransicionarParaRejeitada | GREEN |
| RF-20 | RF-20-T2 | AprovacaoRecursosRestritosTest | rf20T2_rejeicaoSemJustificativa_deveSerRejeitada | GREEN |
| RF-21, RF-22, RF-23 | — | — | — | Ver RC-04 (§1.4), já coberto acima — sem casos adicionais nesta seção. |
| RF-24 | RF-24-T1 | RetiradaDevolucaoMaterialTest | rf24T1_registroDeRetiradaEmReservaEmUso_deveVincularDados | GREEN |
| RF-24 | RF-24-T2 | RetiradaDevolucaoMaterialTest | rf24T2_retiradaDeReservaForaDeEmUso_deveSerRejeitada | GREEN (assunção Q17) |
| RF-24 | RF-24-T3 | RetiradaDevolucaoMaterialTest | rf24T3_novaRetiradaComCicloAberto_deveSerRejeitada | GREEN (assunção Q18) |
| RF-25 | RF-25-T1 | RetiradaDevolucaoMaterialTest | rf25T1_devolucaoComCondicaoIntegro_deveEncerrarCiclo | GREEN |
| RF-25 | RF-25-T2 | RetiradaDevolucaoMaterialTest | rf25T2_devolucaoComCondicaoDanificado_deveSerAceita | GREEN |
| RF-25 | RF-25-T3 | RetiradaDevolucaoMaterialTest | rf25T3_devolucaoSemRetiradaPrevia_deveSerRejeitada | GREEN (assunção Q19) |
| RF-25 | RF-25-T4 | RetiradaDevolucaoMaterialTest | rf25T4_devolucaoSemCondicao_deveSerRejeitada | GREEN (assunção Q20) |
| RF-26 | RF-26-T1 | RetiradaDevolucaoMaterialTest | rf26T1_materialVencidoSemDevolucao_devePossuirPendenciaSinalizada | GREEN |
| RF-26 | RF-26-T2 | RetiradaDevolucaoMaterialTest | rf26T2_jobNoInstanteDoFim_naoDeveSinalizarAindaPendencia | GREEN |
| RF-26 | RF-26-T3 | RetiradaDevolucaoMaterialTest | rf26T3_jobExecutadoDuasVezes_naoDeveDuplicarSinalizacao | GREEN |
| RF-26 | RF-26-T4 | RetiradaDevolucaoMaterialTest | rf26T4_devolucaoAntesDoVencimento_nuncaDeveSinalizarPendencia | GREEN |
| RF-27 | RF-27-T1 | AuditoriaTest | rf27T1_transicaoDeEstadoDeReserva_deveGerarAuditoria | GREEN |
| RF-28 | RF-28-T1 | AuditoriaTest | rf28T1_alteracaoDeCadastro_deveGerarAuditoria | GREEN |
| RF-28 | RF-28-T2 | AuditoriaTest | rf28T2_rejeicaoAutomaticaPorSobreposicao_deveGravarAcaoEspecifica | GREEN (assunção Q21) |
| RF-29 | — | — | — | **SEM TESTE — não testável nesta fase: verificação de ausência de endpoint depende de camada HTTP/controller ainda não implementada; não há stub de controller para exercitar.** |
| RF-30 | RF-30-T1 | AuditoriaTest | rf30T1_deleteDeReservaForaDeSolicitada_deveSerRejeitado | GREEN |
| RF-30 | RF-30-T2 | AuditoriaTest | rf30T2_deleteDeReservaSolicitada_deveSerRejeitadoPorAusenciaDeDelete | GREEN (assunção Q22) |
| RF-31 | RF-31-T1 | NotificacoesTest | rf31T1_mudancaDeEstadoDaReserva_deveNotificarSolicitante | GREEN |
| RF-32 | RF-32-T1 | NotificacoesTest | rf32T1_novaSolicitacaoRestrita_deveNotificarResponsavelCompetente | GREEN |
| RF-32 | RF-32-T2 | NotificacoesTest | rf32T2_recursoRestritoSemResponsavelAssociado_deveRetornarFalha | GREEN (assunção Q23) |
| RF-33 | RF-33-T1 | NotificacoesTest | rf33T1_endpointIndisponivel_devePersistirTransicaoComNotificacaoEmFalha | GREEN (limitação: sem WireMock, simulado em memória) |
| RF-33 | RF-33-T2 | NotificacoesTest | rf33T2_endpointDisponivel_deveRegistrarStatusEnviada | GREEN (limitação: sem WireMock, simulado em memória) |
| RF-34 | RF-34-T1 | RelatoriosTest | rf34T1_relatorioComReservasConhecidas_deveRefletirTodas | GREEN |
| RF-34 | RF-34-T2 | RelatoriosTest | rf34T2_relatorioSemReservasNoPeriodo_deveRetornarZerado | GREEN |
| RF-34 | RF-34-T3 | RelatoriosTest | rf34T3_filtroComFimMenorOuIgualInicio_deveSerRejeitado | GREEN |
| RF-35 | RF-35-T1 | RelatoriosTest | rf35T1_contagemDeRejeicoesPorSobreposicao_deveBaterComAuditoria | GREEN (depende de Q21) |
| RF-36 | RF-36-T1 | RelatoriosTest | rf36T1_solicitanteAcessandoRelatorio_deveRetornar403 | GREEN |
| RF-36 | RF-36-T2 | RelatoriosTest | rf36T2_responsavelOuAdministradorAcessandoRelatorio_devePermitir | GREEN |
| **RF-37** | **RF-37-T1** | — | — | **SEM TESTE — não testável via JUnit/API: requer renderização visual/E2E em viewport, fora do escopo de teste unitário de backend (já indicado no próprio plano).** |
| RF-38 | — | — | — | Coberto implicitamente pelas asserções de HTTP 4xx em todos os casos acima; não há classe dedicada — ver RNF-06-T1 para o aspecto de "mensagem sem stacktrace". |
| **RF-39** | **RF-39-T1** | — | — | **SEM TESTE nesta fase — depende de ferramenta de geração de OpenAPI (springdoc) ainda não configurada no `pom.xml`; teste de contrato/documentação, não de regra de negócio.** |
| RNF-04 | RNF-04-T1 | RequisitosNaoFuncionaisTest | rnf04T1_campoObrigatorioAusente_deveRetornar400 | GREEN |
| RNF-05 | RNF-05-T1 | RequisitosNaoFuncionaisTest | rnf05T1_senhaDeUsuario_nuncaDevePersistirEmTextoPlano | GREEN |
| RNF-06 | RNF-06-T1 | RequisitosNaoFuncionaisTest | rnf06T1_respostaDeErro_naoDeveExporDetalheTecnico | GREEN |
| RNF-08 | — | — | — | Idêntico a RC-03-T4 (já coberto acima); sem caso adicional dedicado. |
| RNF-09 | RNF-09-T1 | RequisitosNaoFuncionaisTest | rnf09T1_integridadeReferencial_naoDeveApresentarRegistrosOrfaos | GREEN |
| EST (J9) | EST-T1 | MaquinaEstadosReservaTest | estT1_solicitadaParaAprovada_devePermitir | GREEN |
| EST (J9) | EST-T2 | MaquinaEstadosReservaTest | estT2_aprovadaParaEmUso_devePermitir | GREEN (assunção Q24 — sem metodo de gatilho dedicado, testado via conflito de reaprovação) |
| EST (J9) | EST-T3 | MaquinaEstadosReservaTest | estT3_emUsoParaConcluida_devePermitir | GREEN (assunção Q24, mesma limitação de EST-T2) |
| EST (J9) | EST-T4 | MaquinaEstadosReservaTest | estT4_solicitadaParaRejeitada_devePermitirComJustificativa | GREEN |
| EST (J9) | EST-T5 | MaquinaEstadosReservaTest | estT5_solicitadaParaCancelada_devePermitirComMotivo | GREEN |
| EST (J9) | EST-T6 | MaquinaEstadosReservaTest | estT6_aprovadaParaCancelada_devePermitirComMotivo | GREEN |
| EST (J9) | EST-T7 | MaquinaEstadosReservaTest | estT7_emUsoParaNaoCompareceu_devePermitir | GREEN (assunção Q25, mesma limitação de gatilho) |
| EST (J9) | EST-T8 | MaquinaEstadosReservaTest | estT8_reservaRestritaPulandoAprovada_deveSerBloqueada | GREEN |
| EST (J9) | EST-T9 | MaquinaEstadosReservaTest | estT9_reservaNaoRestritaJaNasceAprovada_naoDeveEstarEmSolicitada | GREEN (assunção Q24) |
| EST (J9) | EST-T10 | MaquinaEstadosReservaTest | estT10_transicaoAPartirDeRejeitada_deveSerBloqueada | GREEN (assunção Q16) |
| EST (J9) | EST-T11 | MaquinaEstadosReservaTest | estT11_transicaoAPartirDeConcluida_deveSerBloqueada | GREEN (assunção Q16) |
| EST (J9) | EST-T12 | MaquinaEstadosReservaTest | estT12_transicaoAPartirDeCancelada_deveSerBloqueada | GREEN (assunção Q16) |
| EST (J9) | EST-T13 | MaquinaEstadosReservaTest | estT13_transicaoAPartirDeNaoCompareceu_deveSerBloqueada | GREEN (assunção Q16) |

### Regras/casos destacados sem teste nesta fase

| ID | Motivo |
|---|---|
| RC-03-T5 | Gap bloqueante Q4 — o próprio plano instrui explicitamente a não escrever este teste até decisão do responsável sobre idempotência de reenvio (objeção O1, `REVISAO_ATAM.md` §1). |
| RF-29-T1 | Não testável nesta fase — depende de existir uma camada HTTP/controller real para verificar ausência de endpoint de escrita; não há stub de controller no projeto ainda. |
| RF-37-T1 | Não testável via JUnit/teste de API — exige verificação visual de viewport (360px/768px/1920px), fora do escopo de teste de unidade/integração de backend. |
| RF-39-T1 | Não testável nesta fase — depende de ferramenta de geração de especificação OpenAPI (ex. springdoc) ainda não adicionada ao `pom.xml`; é teste de contrato/documentação, não de regra de negócio. |

Nenhuma outra regra do inventário (§1 a §4) ficou sem ao menos um teste JUnit 5 correspondente.

### Correções aplicadas aos 12 testes com fixture ausente (2026-10-27)

Uma primeira rodada da fase GREEN identificou 12 testes que invocavam o mesmo método de
`ReservaService`, `RetiradaMaterialService` ou `NotificacaoService` com um `UUID.randomUUID()`
nunca antes registrado no sistema, sem nenhum passo de `arrange` que materializasse o estado
prévio esperado (ex.: reserva `EM_USO`, reserva já `APROVADA`) — e, ainda assim, pares desses
testes exigiam resultados opostos para o mesmo método, mesma forma de entrada. Essa divergência
foi registrada em detalhe (regra-fonte, decisão provisória "por maioria", pergunta específica) e
apresentada ao responsável do projeto, que autorizou a opção (a): ajustar os testes para criarem
a fixture necessária via as próprias APIs de serviço, preservando a asserção/resultado esperado
original de cada um. Nenhum teste teve seu resultado esperado alterado; apenas o `arrange` foi
completado com dados reais. Correções aplicadas:

| Método de serviço | Testes corrigidos | O que foi adicionado ao `arrange` |
|---|---|---|
| `ReservaService.aprovar` / `rejeitar` | `AprovacaoRecursosRestritosTest.rf19T1`, `rf19T2`, `rf19T4`, `rf20T1`; `MaquinaEstadosReservaTest.estT1`, `estT4` | Criação real de reserva `SOLICITADA` com recurso restrito via `ReservaService.criar`, aprovando-a previamente quando o teste exige estado `APROVADA` (rf19T4). `ReservaService.aprovar`/`rejeitar` deixaram de auto-criar reserva para id desconhecido (agora retornam 409 de forma consistente, sem exceção para esse caso). |
| `ReservaService.alterar` | `ReservaCicloVidaTest.rf13T1`, `rf13T2`, `rf13T3`, `rf13T4` | Criação real da reserva alvo (`SOLICITADA`/`APROVADA`/`EM_USO` conforme o caso) e, em `rf13T4`, de uma segunda reserva ocupando o horário-alvo para gerar colisão real. `alterar` deixou de auto-criar reserva para id desconhecido (passa a retornar 409). |
| `ReservaService.cancelar` | `ReservaCicloVidaTest.rf14T1`, `rf14T2`, `rf14T3`, `rf14T5`; `MaquinaEstadosReservaTest.estT5`, `estT6`; `AutenticacaoAutorizacaoTest.rf03T1`, `rf03T2`, `rf03T3` | Criação real da reserva do solicitante (e, quando aplicável, transição explícita para `CONCLUIDA`/`CANCELADA` antes da nova chamada). `cancelar`/`consultar` deixaram de auto-criar reserva para id desconhecido. |
| `ReservaService.excluir` | `AuditoriaTest.rf30T2` | Criação real de reserva `SOLICITADA` antes do `excluir`, para exercitar o ramo HTTP 405 com estado real (o comportamento para RF-30-T1, id desconhecido → 403, já era consistente e não precisou de ajuste). |
| `RetiradaMaterialService.registrarRetirada` / `registrarDevolucao` | `RetiradaDevolucaoMaterialTest.rf24T1`, `rf24T3`, `rf25T1`, `rf25T2`, `rf26T1`, `rf26T2`, `rf26T3`, `rf26T4` | Criação real de reserva `EM_USO` (via `ReservaService.criar` + `setEstado`) antes de `registrarRetirada`, e uso do id de retirada retornado (em vez de um id aleatório) antes de `registrarDevolucao`. Os dois serviços deixaram de aceitar id desconhecido implicitamente (passam a exigir reserva/retirada real, retornando 409 quando ausente — ver `rf24T2`/`rf25T3`, que já esperavam esse 409 e continuam GREEN). |
| `NotificacaoService.notificarTransicaoDeEstado` | `NotificacoesTest.rf33T1` | Como os dois testes (`rf33T1`/`rf33T2`) não tinham nenhum parâmetro de entrada capaz de diferenciar "endpoint disponível" de "indisponível" (conflito estrutural, não de fixture), foi adicionado um pequeno ponto de injeção opcional — `NotificacaoService.NotificadorExterno` (interface funcional) e um construtor alternativo `NotificacaoService(NotificadorExterno)` — mantendo o construtor padrão com o comportamento anterior (sempre `ENVIADA`). Apenas `rf33T1` foi alterado, instanciando `new NotificacaoService(r -> false)` para simular indisponibilidade explicitamente; `rf33T2` continua usando o serviço padrão, sem alteração. |

Resultado: `mvn verify` executado em 2026-10-27 após as correções — **123 testes, 123 verdes, 0
falhas, BUILD SUCCESS**.
