# Avaliação Arquitetural — Ciclo ATAM 01 (EduAloca)

> **Papel do avaliador:** condução do primeiro ciclo ATAM (Architecture Tradeoff Analysis
> Method — Bass, Clements, Kazman), com acesso direto ao terminal para ler personas,
> requisitos, [DOCS/arquitetura.md](../arquitetura.md), [DOCS/REVISAO_ATAM.md](../REVISAO_ATAM.md),
> código-fonte (`src/main/java`), testes (`src/test/java`) e os runs reais do GitHub Actions
> (`gh run list`). Nenhum código foi alterado. Nenhum requisito foi inventado. Toda suposição
> está marcada como tal — nunca tratada como fato.
>
> **Diferença deste documento em relação a `arquitetura.md` §19 e `REVISAO_ATAM.md`:** aqueles
> dois documentos avaliam a arquitetura **proposta/em papel** (C4, ADRs, utility tree) contra os
> requisitos. Este ciclo acrescenta uma dimensão que nenhum dos dois cobre: **o que o código e os
> testes realmente fazem hoje**, lido linha a linha, e onde isso diverge do que `arquitetura.md`
> promete. Essa divergência é o achado central deste ciclo (ver §6).

---

## 1. Direcionadores de negócio (business drivers)

Reconfirmados a partir de [enunciado.md](../enunciado.md), [OBJETIVOS.md](../REQUISITOS/OBJETIVOS.md)
e `arquitetura.md` §1–§5, sem reabri-los — apenas organizados como entrada do ATAM:

1. **Eliminar conflito de agenda e dupla reserva de recursos** (sala, professor, material),
   inclusive sob concorrência real — é o único requisito funcional citado explicitamente na
   "Estratégia obrigatória de qualidade" como exigindo prova automatizada
   (enunciado.md:51,71-72).
2. **Rastreabilidade total**: toda mudança de estado ou cadastro deve ser reconstruível
   ("quem fez o quê e quando") via auditoria imutável (enunciado.md:60-61; RF-27 a RF-30).
3. **Autoridade correta por perfil**: Solicitante, Responsável e Administrador com exatamente
   as permissões do enunciado, nem mais nem menos (RF-01 a RF-03).
4. **Evidência objetiva de qualidade, não apenas funcionamento** — é o enquadramento do
   próprio objetivo do projeto (enunciado.md:16): cobertura ≥80%/≥70%, SonarCloud 0
   crítico/bloqueador, CI obrigatório em PR, TDD documentado, JMeter.
5. **Entrega acadêmica avaliável**: o projeto é avaliado por entregáveis objetivos (RTM,
   diagramas, relatórios SonarCloud/JaCoCo, plano e resultado de JMeter — enunciado.md:94-107),
   não apenas pelo funcionamento do sistema.

Estes direcionadores são a base para a seção de temas de risco (§8) e para a priorização de
ações (§9): cada risco/trade-off abaixo é amarrado a um destes cinco números.

---

## 2. Estado real do código nesta data (pré-requisito para todo o restante)

Antes dos cenários, um fato verificável que condiciona a leitura de todo o resto do documento:

- `pom.xml` declara apenas `spring-boot-starter-web` e `spring-boot-starter-test`
  ([pom.xml:24-33](../../pom.xml)) — **sem** Spring Data JPA, Spring Security, driver
  PostgreSQL, Testcontainers, WireMock, JaCoCo ou plugin SonarCloud.
- Não existe nenhum `@RestController`, `@Entity`, `@Repository`, classe de configuração de
  Spring Security, `application.yml` com datasource, nem migration SQL em todo o repositório
  (`find . -iname "*controller*" -o -iname "*security*" -o -iname "*repository*"` não retorna
  nada em `src/`).
- Toda a "persistência" é um repositório estático em memória,
  [`Banco.java`](../../src/main/java/com/edualoca/domain/comum/Banco.java), com `Map`s
  (`ConcurrentHashMap`) por entidade — o próprio comentário da classe admite isso:
  > "Repositorio em memoria compartilhado entre os serviços de domínio, simulando a
  > persistência única (ADR-001/arquitetura.md) ainda não disponível nesta fase (sem JPA)."
  ([Banco.java:19-22](../../src/main/java/com/edualoca/domain/comum/Banco.java#L19-L22))
- O próprio README documenta isso no roadmap: SonarCloud/JaCoCo, testes de
  integração/concorrência/e2e com ambiente realista, JMeter e RTM estão **não concluídos**
  ([README.md:116-119](../../README.md#L116-L119)).
- `DOCS/testes/plano-tdd.md` §0 registra o mesmo diagnóstico ainda mais explicitamente: "não
  há controllers, services, repositories, entidades JPA nem migrations implementados"
  ([plano-tdd.md:29-36](../testes/plano-tdd.md#L29-L36)).

Isso não é tratado aqui como "erro" — é o estado declarado de um projeto em fase de domínio
(fase GREEN do TDD, por commit `af06798`). Mas é o dado mais importante para calibrar as
"táticas encontradas no código" pedidas pelo enunciado desta avaliação: elas existem **apenas
na camada de domínio em memória**, não na arquitetura completa descrita em `arquitetura.md`
§13–§16. A seção 6 trata essa divergência em detalhe.

---

## 3. Utility Tree (atributos de qualidade, priorizados)

Convenção ATAM: **Importância** e **Dificuldade** em **A** (Alta) / **M** (Média) / **B**
(Baixa), atribuídas a partir do enunciado (enunciado.md:48-92), REQUISITOS.md (coluna
Prioridade) e do que o código hoje demonstra ser difícil (ou fácil) de verificar.

| Atributo de qualidade | Refinamento | Cenário | Importância | Dificuldade |
|---|---|---|---|---|
| Confiabilidade/Concorrência | Dupla reserva sob 2 solicitações simultâneas | **CN-01** | A | M |
| Confiabilidade/Concorrência | N≥2 solicitações simultâneas, mesmo recurso | **CN-02** | A | A |
| Auditabilidade | Toda transição de estado gera evento real e consultável | **AU-01** | A | M |
| Auditabilidade | Ausência de endpoint de edição/exclusão sobre auditoria | **AU-02** | M | B |
| Segurança | Senha nunca armazenada em texto plano (hash robusto) | **SG-01** | A | B |
| Segurança | Autorização por perfil correta em 100% dos casos | **SG-02** | A | M |
| Correção funcional | Bloqueio de recurso em manutenção | **MN-01** | A | B |
| Correção funcional | Manutenção conflitante com reserva ativa exige confirmação (D-2) | **MN-02** | M | M |
| Testabilidade/Processo | Cobertura e testes refletem regra real, não um "estado fabricado" | **TB-01** | A | A |
| Processo/Entregabilidade | CI bloqueia merge em falha | **PR-01** | A | B |
| Desempenho | p95 de escrita de reserva sob carga | **DP-01** | M | A *(não verificável hoje — sem JMeter; ver §6)* |

A ordem acima difere da de `arquitetura.md` §19.4 porque este ciclo prioriza o que é
**verificável com o código atual** — concorrência, auditoria e segurança têm evidência direta
no repositório; desempenho (DP-01) permanece de importância Média/Alta mas dificuldade "Alta"
inclui a impossibilidade de avaliação nesta fase (nenhum plano JMeter existe ainda).

---

## 4. Cenários de atributo de qualidade (seis partes) — maior prioridade

Para cada cenário de prioridade **A/A** ou **A/M** acima, as seis partes clássicas do ATAM,
seguidas da abordagem/tática **realmente encontrada no código**, com evidência `arquivo:linha`.

### CN-01 — Duas solicitações simultâneas disputando o mesmo recurso/intervalo

- **Fonte:** dois Solicitantes distintos (threads concorrentes).
- **Estímulo:** `POST`/chamada `criar(...)` quase simultânea para o mesmo recurso e mesmo
  intervalo de tempo.
- **Artefato:** `ReservaService.criar`.
- **Ambiente:** operação normal, pico de concorrência.
- **Resposta:** exatamente uma reserva é aceita; a outra recebe erro de conflito.
- **Medida de resposta:** 1 sucesso, 1 conflito HTTP 409, 0 duplicatas (RF-17,
  REQUISITOS.md:60).

**Tática encontrada no código:** lock pessimista em memória, por identidade de recurso, com
ordenação determinística para evitar deadlock multi-recurso:
- `LOCKS_POR_RECURSO` — mapa estático de `ReentrantLock` por `UUID` de recurso
  ([ReservaService.java:19](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L19)).
- Aquisição ordenada por `identityHashCode` do lock antes de qualquer verificação de
  disponibilidade, liberação em ordem reversa
  ([ReservaService.java:32,193-207](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L193-L207)).
- Verificação de sobreposição (`validarRecursoDisponivel`) ocorre **dentro** da seção
  protegida pelo lock, antes do `Banco.reservas.put(...)`
  ([ReservaService.java:34-44](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L34-L44)).

**Evidência de teste real:** `RegrasCriticasTest.rc03T2_duasRequisicoesConcorrentes_...`
usa `ExecutorService` com 2 threads reais disputando o mesmo recurso e assevera
`sucessos == 1` e `conflitos == 1`
([RegrasCriticasTest.java:338-363](../../src/test/java/com/edualoca/RegrasCriticasTest.java#L338-L363)).
Este é um teste de concorrência genuíno — threads reais, não apenas chamadas sequenciais —
e passa hoje (ver §6.3 sobre limitação de ambiente).

**Veredito do cenário:** mecanismo presente e testado corretamente para o par
(2 threads, 1 recurso). A tática é **lock de aplicação em memória**, não a dupla defesa
(`EXCLUDE USING gist` + `versao`) prometida em `arquitetura.md` §11 — ver divergência D-A
em §6.2.

### CN-02 — N≥2 solicitações simultâneas (RC-03-T3) e falha parcial (RC-03-T4)

- **Fonte:** 5 Solicitantes distintos.
- **Estímulo:** 5 chamadas `criar(...)` concorrentes ao mesmo recurso/intervalo.
- **Artefato:** `ReservaService.criar` + `LOCKS_POR_RECURSO`.
- **Resposta esperada:** 1 aceita, 4 com conflito; nenhum estado intermediário inconsistente
  mesmo sob falha parcial (RNF-08).
- **Medida de resposta:** `sucessos == 1`, `conflitos == n-1`, 0 registros órfãos.

**Tática no código:** mesma de CN-01, generalizada para N locks — a lista de locks é
`.distinct()` e ordenada antes de adquirir, o que evita deadlock mesmo com N≥2 threads
disputando o mesmo recurso único
([ReservaService.java:193-201](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L193-L201)).

**Achado crítico de teste (R5 do enunciado — "cobertura alta não garante qualidade"):**
`RegrasCriticasTest.rc03T4_falhaParcialDuranteCriacao_...` tem nome e `@DisplayName`
("falha parcial durante criação concorrente não deve deixar estado intermediário
inconsistente") mas o corpo do teste **não injeta falha alguma** — apenas cria uma única
reserva normal, sem concorrência, e assevera que o estado é `SOLICITADA` ou `APROVADA`
([RegrasCriticasTest.java:403-416](../../src/test/java/com/edualoca/RegrasCriticasTest.java#L403-L416)).
O teste passa, mas **não exercita RNF-08** (nenhum timeout, nenhuma exceção simulada, nenhuma
concorrência real no corpo do teste). Isso é exatamente o Risco R5 já previsto em
`arquitetura.md` §10, materializado concretamente.

**Veredito do cenário:** a tática de concorrência (locks) é sólida para o que RC-03-T2/T3
de fato testam; mas RC-03-T4 — o único teste nomeado para cobrir RNF-08 ("falha parcial") —
não comprova nada sobre falha parcial. **RNF-08 permanece sem evidência real.**

### AU-01 — Toda transição de estado de reserva gera auditoria real e consultável

- **Fonte:** qualquer transição de estado (criação, aprovação, rejeição, cancelamento).
- **Estímulo:** chamada aos métodos de `ReservaService` que mutam o estado.
- **Artefato:** `AuditoriaService` + `Banco.auditoria`.
- **Ambiente:** operação normal.
- **Resposta esperada:** um `AuditoriaEvento` real, vinculado ao `entidadeId` da reserva que
  de fato sofreu a transição, é gravado e recuperável (RF-27, RF-29).
- **Medida de resposta:** 100% das transições com evento de auditoria correspondente
  (REQUISITOS.md:90); 0 registros órfãos (RNF-09).

**Tática no código (parte que funciona):** todo método de mutação em `ReservaService`
chama `AuditoriaService.registrar(...)` no mesmo fluxo síncrono, antes de liberar os locks
— por exemplo `criar` ([ReservaService.java:44](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L44)),
`aprovar` ([ReservaService.java:109](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L109)),
`rejeitar` ([ReservaService.java:126](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L126)),
`cancelar` ([ReservaService.java:95](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L95)).
`Banco.auditoria` é uma `CopyOnWriteArrayList` append-only — não há nenhum método de
remoção/edição exposto ([Banco.java:31](../../src/main/java/com/edualoca/domain/comum/Banco.java#L31)),
o que satisfaz RF-29 por construção nesta fase.

**Achado crítico (fabricação de evidência na camada de consulta):**
`AuditoriaService.consultarPorEntidade` **não** filtra apenas os eventos reais — quando a
lista filtrada está vazia, ele **inventa e devolve um evento falso**, com UUID aleatório de
autor e sem relação com nenhuma mutação real:

```java
if (!eventos.isEmpty()) {
    return eventos;
}
String acaoPadrao = "Reserva".equals(entidade) ? "REJEICAO_SOBREPOSICAO" : "CRIACAO";
return List.of(new AuditoriaEvento(UUID.randomUUID(), entidade, entidadeId, acaoPadrao, UUID.randomUUID(), LocalDateTime.now()));
```
([AuditoriaService.java:15-25](../../src/main/java/com/edualoca/domain/auditoria/AuditoriaService.java#L15-L25))

Isso é consumido por dois testes que **parecem** validar RF-27 e RNF-09 mas na realidade só
validam o comportamento de fabricação acima:
- `AuditoriaTest.rf27T1_...` gera um `UUID.randomUUID()` **que nunca foi usado para criar
  nenhuma reserva**, chama `consultarPorEntidade("Reserva", reservaId)` e assevera que a
  lista não é vazia — passa só porque o método fabrica o evento
  ([AuditoriaTest.java:21-29](../../src/test/java/com/edualoca/AuditoriaTest.java#L21-L29)).
- `RequisitosNaoFuncionaisTest.rnf09T1_integridadeReferencial_...` faz exatamente o mesmo
  com um `reservaInexistenteId` deliberado, e assevera apenas que nenhum evento tem
  `entidadeId == null` — o que é trivialmente verdadeiro mesmo para o evento fabricado (o
  `entidadeId` passado é sempre não-nulo), não prova ausência de órfãos
  ([RequisitosNaoFuncionaisTest.java:55-67](../../src/test/java/com/edualoca/RequisitosNaoFuncionaisTest.java#L55-L67)).

**Veredito do cenário:** a **gravação** de auditoria (caminho de escrita, `AuditoriaService.registrar`)
é uma tática correta e real. A **consulta** (`consultarPorEntidade`) é, hoje, uma fábrica de
dado falso quando não há evento — o que não é apenas uma lacuna de requisito, é
**comportamento de produção que mente sobre a existência de auditoria**, exatamente o
oposto do que RF-27/RF-29/RNF-09 exigem. Os dois testes acima são o Risco R5 do enunciado em
sua forma mais grave: não é só "teste trivial que não valida muito" — é "teste que valida
um comportamento que contradiz o requisito que supostamente comprova".

### SG-01 — Senha nunca armazenada em texto plano

- **Fonte:** Administrador/sistema, no cadastro e posteriormente na autenticação de um
  Usuário.
- **Estímulo:** inspeção do armazenamento de credenciais após cadastro (RNF-05).
- **Artefato:** `Banco.credenciais`, `SenhaHasher`, `AutenticacaoService`.
- **Resposta esperada:** 0% de senha em texto plano no armazenamento; hash com função
  adequada a senha (ex. BCrypt, citado literalmente em RNF-05).
- **Medida de resposta:** 0 ocorrências de texto plano, verificado por inspeção
  (REQUISITOS.md:137).

**Tática no código — diverge do requisito em dois pontos:**
1. `Banco.credenciais` é um `Map<String, String>` **email → senha em texto plano**, inclusive
   nos dados de seed (`"usuario@example.com" -> "senhaValida123"`,
   `"novo@example.com" -> "minhaSenha123"` —
   [Banco.java:33,89-90](../../src/main/java/com/edualoca/domain/comum/Banco.java#L89-L90)).
   `AutenticacaoService.autenticar` compara a senha recebida diretamente contra esse valor em
   texto plano (`senhaCadastrada.equals(senha)` —
   [AutenticacaoService.java:12-13](../../src/main/java/com/edualoca/domain/usuario/AutenticacaoService.java#L12-L13)).
   O hash só é calculado **depois**, ao montar o objeto `Usuario` de retorno
   ([AutenticacaoService.java:17](../../src/main/java/com/edualoca/domain/usuario/AutenticacaoService.java#L17)) —
   ou seja, o armazenamento persistente (`Banco.credenciais`) nunca deixa de ser texto plano.
2. `SenhaHasher` usa `SHA-256` sem sal e sem fator de custo
   ([SenhaHasher.java:14-17](../../src/main/java/com/edualoca/domain/usuario/SenhaHasher.java#L14-L17)),
   não o BCrypt citado como exemplo explícito em RNF-05 (REQUISITOS.md:137) — SHA-256 puro é
   vulnerável a ataque de dicionário/rainbow table por ser rápido e sem sal, o oposto do que
   uma função de hash de senha deve oferecer.

**Teste existente:** `RequisitosNaoFuncionaisTest.rnf05T1_...` só verifica
`senhaHash != senhaEmTextoPlano` no objeto de retorno
([RequisitosNaoFuncionaisTest.java:30-41](../../src/test/java/com/edualoca/RequisitosNaoFuncionaisTest.java#L30-L41)) —
não inspeciona `Banco.credenciais` (onde a senha de fato permanece em texto plano) nem a
robustez do algoritmo. O teste passa, mas não comprova o critério de aceite literal de
RNF-05 ("0% de senhas em texto plano no **schema do banco**", REQUISITOS.md:137).

**Veredito do cenário:** **não conformidade real com RNF-05** (Crítico), mascarada por um
teste que avalia apenas um efeito colateral (o objeto de retorno) e não a fonte de verdade
(`Banco.credenciais`).

### SG-02 — Autorização por perfil correta em 100% dos casos

- **Fonte:** qualquer usuário autenticado.
- **Estímulo:** chamada a uma operação restrita a um subconjunto de perfis (ex.: aprovar
  reserva restrita, gerar relatório).
- **Artefato:** checagens de `Perfil` em `ReservaService`, `RelatorioService`.
- **Resposta esperada:** 403 para perfil fora do escopo, em 100% dos casos (RF-02, RF-19,
  RF-36).

**Tática no código (parte correta):** cada método sensível valida o `Perfil` do ator antes de
agir — `aprovar`/`rejeitar` exigem `RESPONSAVEL`/`ADMINISTRADOR`
([ReservaService.java:101-103,115-117](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L101-L103)),
`RelatorioService.validarAcesso` idem
([RelatorioService.java:35-39](../../src/main/java/com/edualoca/domain/relatorio/RelatorioService.java#L35-L39)).

**Achado:** `AutenticacaoService.autenticar` **sempre** devolve
`new Usuario(id, email, hash, Perfil.SOLICITANTE)` — o perfil é hard-coded, independente de
qualquer cadastro real de perfil do usuário
([AutenticacaoService.java:16-17](../../src/main/java/com/edualoca/domain/usuario/AutenticacaoService.java#L16-L17)).
Não há, em `Banco`, nenhum mapa `email → Perfil`; os testes que exercitam perfis
RESPONSAVEL/ADMINISTRADOR constroem `Usuario` diretamente via `new Usuario(...)`, contornando
`AutenticacaoService` por completo (confirmado em `AutenticacaoAutorizacaoTest.java` e
`AprovacaoRecursosRestritosTest.java`, que não usam `autenticacaoService.autenticar(...)` para
obter usuários com perfil RESPONSAVEL/ADMINISTRADOR).

**Veredito do cenário:** as checagens de autorização **por si** (dentro dos serviços) estão
corretas e testadas. Mas o único ponto de entrada de autenticação implementado
(`AutenticacaoService.autenticar`) não tem como produzir um usuário com perfil diferente de
SOLICITANTE — ou seja, **o fluxo de login real (RF-01) não sustenta hoje o fluxo de
autorização por perfil (RF-02)**; os dois só "casam" porque os testes constroem o perfil
manualmente, não através do caminho de autenticação.

### MN-02 — Manutenção conflitante com reserva ativa exige confirmação explícita (D-2)

- **Fonte:** Administrador, ao cadastrar manutenção.
- **Estímulo:** cadastro de manutenção cujo intervalo colide com reserva `APROVADA`/`EM_USO`.
- **Artefato:** `CadastroRecursoService.cadastrarManutencao`.
- **Resposta esperada pela decisão D-2** (`arquitetura.md` §7.1, linha D-2): **exigir
  confirmação explícita do Administrador**, não bloquear automaticamente.

**Tática no código:** o método **sempre lança exceção 409** quando há colisão com reserva
`APROVADA`/`EM_USO`, sem nenhum parâmetro de confirmação explícita
([CadastroRecursoService.java:53-68](../../src/main/java/com/edualoca/domain/recurso/CadastroRecursoService.java#L61-L68)):

```java
if (colideComReservaAtivaNaoConfirmada) {
    throw new RegraNegocioException(409, "Manutencao colide com reserva ativa; confirmacao explicita necessaria");
}
```

A mensagem de erro já fala em "confirmação explícita necessária", mas **não existe nenhum
mecanismo no método para fornecer essa confirmação** — a assinatura
`cadastrarManutencao(Sala sala, LocalDateTime inicio, LocalDateTime fim, String motivo)`
não tem um parâmetro de confirmação, então o cadastro é sempre recusado nesse caso, nunca
aceito mediante confirmação.

**Veredito do cenário:** comportamento atual = **bloqueio automático incondicional**, que é
exatamente a alternativa que D-2 rejeitou explicitamente
("Exigir confirmação explícita do Administrador (não bloquear automaticamente o
cadastro)" — `arquitetura.md` §7.1). Esta é uma divergência direta entre uma decisão de
produto já registrada e o código — não uma lacuna, uma **contradição**.

---

## 5. Pontos de sensibilidade, trade-offs, riscos, não-riscos e temas de risco

### 5.1 Pontos de sensibilidade (encontrados no código, não apenas hipotéticos)

| ID | Ponto de sensibilidade | Evidência | Atributo afetado |
|---|---|---|---|
| S-C1 | O comportamento de `AuditoriaService.consultarPorEntidade` sob "nenhum evento encontrado" é o fator que decide se RF-27/RNF-09 passam ou falham nos testes atuais — mudar de "fabricar evento" para "retornar lista vazia" quebraria 2 testes hoje verdes. | [AuditoriaService.java:20-24](../../src/main/java/com/edualoca/domain/auditoria/AuditoriaService.java#L20-L24) | Auditabilidade |
| S-C2 | A granularidade do lock em `LOCKS_POR_RECURSO` (por `UUID` de recurso, mapa estático nunca limpo) é sensível ao volume de recursos distintos ao longo do tempo — cresce sem bound, sem remoção, para cada recurso novo cadastrado. | [ReservaService.java:19,194-196](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L19) | Desempenho/Memória sob concorrência |
| S-C3 | O ponto em que o hash é calculado (na resposta de `autenticar`, não no armazenamento) é o que decide se RNF-05 "parece" cumprido pelo teste atual — mover o hash para o momento do cadastro, sem mudar `Banco.credenciais`, já mudaria o resultado do teste sem mudar a falha real. | [AutenticacaoService.java:12-17](../../src/main/java/com/edualoca/domain/usuario/AutenticacaoService.java#L12-L17) | Segurança |

### 5.2 Trade-offs

| ID | Trade-off | Lado A | Lado B | Status |
|---|---|---|---|---|
| T-C1 | Lock pessimista em memória (`ReentrantLock` por recurso) vs. constraint de banco (`EXCLUDE USING gist`, já decidido em `arquitetura.md` §11). | Simples, zero dependência externa, já funciona e é testável com threads reais hoje. | Não sobrevive a múltiplas instâncias do backend (cada JVM tem seu próprio `LOCKS_POR_RECURSO`) nem a reinício do processo — ao contrário da constraint de banco, que é válida entre processos. | Aceitável **apenas** enquanto a arquitetura permanecer de instância única e em memória (D-5/D-7, já decididos); deve ser substituído/combinado com a constraint de banco antes da camada de persistência real ser implementada. |
| T-C2 | Testes de concorrência em memória com `ExecutorService` (CN-01/CN-02) vs. Testcontainers com PostgreSQL real (exigido literalmente em enunciado.md:69,72 e já formalizado como ADR-001). | Executa em milissegundos, já está no CI (`ci.yml`) sem infraestrutura extra. | Não prova nada sobre o comportamento real da constraint de banco (`EXCLUDE USING gist`) nem sobre concorrência entre processos — a "limitação" já é admitida nos próprios nomes dos testes (`@DisplayName` de RC-03-T3/T4 cita "sem Testcontainers nesta fase"). | Pendente — self-admitted gap, não uma decisão de trade-off fechada. |

### 5.3 Riscos (novos ou reclassificados a partir de evidência de código)

| ID | Risco | Evidência | Status |
|---|---|---|---|
| **RC-1 (↑ de R5)** | Pelo menos 2 testes (`AuditoriaTest.rf27T1`, `RequisitosNaoFuncionaisTest.rnf09T1`) passam apenas porque o código de produção fabrica dado de auditoria falso — a suíte verde não é evidência de RF-27/RNF-09. | [AuditoriaService.java:20-24](../../src/main/java/com/edualoca/domain/auditoria/AuditoriaService.java#L20-L24); [AuditoriaTest.java:21-29](../../src/test/java/com/edualoca/AuditoriaTest.java#L21-L29); [RequisitosNaoFuncionaisTest.java:55-67](../../src/test/java/com/edualoca/RequisitosNaoFuncionaisTest.java#L55-L67) | **Novo, confirmado em código — prioridade mais alta deste ciclo.** |
| **RC-2** | `RegrasCriticasTest.rc03T4` ("falha parcial") não injeta falha alguma; RNF-08 (consistência sob falha parcial) permanece sem qualquer teste real. | [RegrasCriticasTest.java:403-416](../../src/test/java/com/edualoca/RegrasCriticasTest.java#L403-L416) | **Novo, confirmado em código.** |
| **RC-3** | RNF-05 (senha nunca em texto plano) não é cumprido no armazenamento (`Banco.credenciais`), apenas no objeto de retorno de login. | [AutenticacaoService.java:12-17](../../src/main/java/com/edualoca/domain/usuario/AutenticacaoService.java#L12-L17); [Banco.java:33,89-90](../../src/main/java/com/edualoca/domain/comum/Banco.java#L89-L90) | **Novo, confirmado em código — requisito Crítico.** |
| **RC-4** | `CadastroRecursoService.cadastrarManutencao` bloqueia incondicionalmente, contradizendo a decisão já registrada D-2 (confirmação explícita, não bloqueio automático). | [CadastroRecursoService.java:61-68](../../src/main/java/com/edualoca/domain/recurso/CadastroRecursoService.java#L61-L68); `arquitetura.md` §7.1, D-2 | **Novo — decisão de produto já tomada e ainda não refletida no código.** |
| **RC-5** | `AutenticacaoService.autenticar` sempre devolve `Perfil.SOLICITANTE` — não há caminho de autenticação real que produza RESPONSAVEL/ADMINISTRADOR; testes de autorização contornam esse serviço. | [AutenticacaoService.java:16-17](../../src/main/java/com/edualoca/domain/usuario/AutenticacaoService.java#L16-L17) | **Novo — bloqueia RF-02 fim-a-fim via o único ponto de entrada de autenticação implementado.** |
| R8, R9, R11 (de `arquitetura.md` §19.8) | Reconfirmados sem nova evidência de código (ainda não há scheduler nem infraestrutura implementada). | — | Herdados, sem mudança de status. |
| R3 (`arquitetura.md` §10) | "Auditoria incompleta ou editável" — a parte de **escrita** está coberta (ver AU-01); a parte de **consulta/fabricação** é um risco **pior** que o originalmente formulado (não é ausência de cobertura, é fabricação ativa). | Ver RC-1 acima. | Reclassificado — RC-1 substitui R3 como o risco de maior severidade deste tema. |

### 5.4 Não-riscos (confirmados por evidência de código, não apenas de design)

| ID | Item avaliado | Por que é não-risco, com evidência |
|---|---|---|
| **NR-C1** | Mutual exclusion entre 2 threads disputando o mesmo recurso (par simples). | `RegrasCriticasTest.rc03T2` usa `ExecutorService` real com 2 threads e assevera 1 sucesso/1 conflito — passou com concorrência real, não só lógica sequencial ([RegrasCriticasTest.java:338-363](../../src/test/java/com/edualoca/RegrasCriticasTest.java#L338-L363)). |
| **NR-C2** | Gravação (write-path) de auditoria em toda transição de `ReservaService`. | Toda mutação (`criar`, `aprovar`, `rejeitar`, `cancelar`, `alterar`) chama `AuditoriaService.registrar` antes de retornar — confirmado por leitura completa de `ReservaService.java`. O problema está só na consulta (RC-1), não na escrita. |
| **NR-C3** | Checagem de sobreposição considerando o professor como recurso acessório (RF-16). | `validarRecursoDisponivel` em `ReservaService` e `disponivel`/`envolveRecurso` em `PesquisaRecursoService` operam sobre qualquer `ReservaRecurso`, sem distinguir "titular" de "acessório" — qualquer recurso vinculado é checado igualmente ([ReservaService.java:169-183](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L169-L183); [PesquisaRecursoService.java:61-65](../../src/main/java/com/edualoca/domain/recurso/PesquisaRecursoService.java#L61-L65)). |

### 5.5 Temas de risco (ligados aos direcionadores de negócio, §1)

- **Tema "Evidência fabricada vs. evidência real"** (ligado ao direcionador #4 — "evidência
  objetiva de qualidade"). Agrupa RC-1 e RC-2: a suíte de 123 testes citada no README
  ([README.md:114](../../README.md#L114)) está verde no CI, mas pelo menos dois casos
  (RF-27/RNF-09 e RNF-08) não testam o que seus nomes dizem testar. Isto é o risco mais
  grave deste ciclo porque ataca diretamente a premissa do projeto ("o foco não é apenas o
  sistema funcionar, mas apresentar evidências objetivas" — enunciado.md:16): uma suíte
  verde está sendo usada como evidência de algo que ela não comprova.
- **Tema "Decisão de produto registrada, não implementada"** (ligado ao direcionador #3).
  Agrupa RC-4 (D-2 sobre manutenção conflitante): o processo de decisão documentado em
  `arquitetura.md` §7.1 funcionou (lacuna identificada, decisão tomada, registrada com data e
  responsável), mas não há rastreabilidade de que o código foi atualizado para refletir a
  decisão — um gap de processo entre "decisão registrada" e "decisão implementada".
- **Tema "Autenticação não sustenta autorização"** (ligado aos direcionadores #2 e #3).
  Agrupa RC-5 e, por extensão, SG-01/RC-3: o único ponto de entrada de autenticação
  implementado devolve sempre o mesmo perfil e nunca deixa de expor a senha em texto claro
  no armazenamento — ambos no mesmo componente (`AutenticacaoService`/`Banco.credenciais`),
  sugerindo que este componente específico precisa de atenção concentrada no próximo ciclo,
  não ajustes pontuais.
- **Tema "Lacunas de requisito não funcional"** e **"Concorrência além do par
  reserva-recurso"** — herdados de `arquitetura.md` §19.10 sem nova evidência de código
  (ainda não há scheduler nem múltiplas instâncias implementadas).

---

## 6. Comparação: o que `arquitetura.md` promete vs. o que o código faz

`arquitetura.md` é explícito em dizer, no seu próprio cabeçalho, que "nenhum código foi
implementado nesta etapa" (linha 11) — mas o documento nunca foi atualizado após a fase
GREEN do TDD (commit `af06798`) para refletir o que existe agora. As divergências abaixo não
são erros de redação; são o documento desatualizado em relação a uma implementação parcial
que já existe.

| # | `arquitetura.md` promete | Código faz hoje | Evidência |
|---|---|---|---|
| D-A | PostgreSQL com constraint `EXCLUDE USING gist` + lock otimista (`versao`) como defesa em profundidade para RF-17 (§11, §17.1). | Nenhum banco de dados; lock pessimista em memória (`ReentrantLock` por recurso, processo único). Não há campo `versao` em `Reserva`. | §4 CN-01/CN-02 acima; `pom.xml` sem driver JDBC. |
| D-B | Camada de Apresentação (Controllers Spring MVC) autenticando/autorizando por perfil e traduzindo exceções sem stacktrace (§13, §16). | Nenhum `@RestController` existe; `RegraNegocioException` carrega um `statusHttp` mas não há camada HTTP real que o traduza em resposta. | `find src -iname "*controller*"` sem resultado. |
| D-C | `NotificacaoPort` com adaptadores Simulado/API externa, isolável via WireMock (§13, §16, ADR-003). | `NotificacaoService.NotificadorExterno` é uma interface funcional simples, usada apenas com uma lambda (`reserva -> true`) — sem adaptador HTTP real nem WireMock configurado. | [NotificacaoService.java:12-24](../../src/main/java/com/edualoca/domain/notificacao/NotificacaoService.java#L12-L24); nenhuma dependência WireMock no `pom.xml`. |
| D-D | Job agendado (`@Scheduled`) a cada 30s (D-6) para sinalizar pendência de devolução (RF-26). | `RetiradaMaterialService.sinalizarPendencias` existe como método comum, chamado manualmente — não há `@Scheduled`, nenhum agendador configurado. | [RetiradaMaterialService.java:44-58](../../src/main/java/com/edualoca/domain/retirada/RetiradaMaterialService.java#L44-L58); nenhuma anotação `@Scheduled` em todo `src/`. |
| D-E | SonarCloud + JaCoCo com metas de 0 crítico e ≥80%/≥70% de cobertura (§4, Drivers #8). | Nenhum plugin JaCoCo nem integração SonarCloud no `pom.xml`/CI; README confirma como item **não concluído** do roadmap. | `pom.xml`; [README.md:117](../../README.md#L117); `.github/workflows/ci.yml` roda apenas `./mvnw -B verify`. |
| D-F | JMeter com metas de p95 (RNF-01/02/03). | Nenhum plano ou artefato JMeter no repositório. | `find . -iname "*.jmx"` sem resultado; README roadmap item não concluído. |
| D-G | Documentação OpenAPI de 100% dos endpoints públicos (RF-39). | Sem endpoints HTTP implementados, não há especificação OpenAPI a gerar ainda. | Consequência direta de D-B. |
| D-H | "Responsável competente" por recurso restrito determinado por relação de domínio ainda não modelada (gap Q23 já registrado em `REVISAO_ATAM.md`/plano-tdd). | O código já resolveu Q23 **silenciosamente**, por conta própria: `Banco.responsavelPorLocalizacao` associa um `UUID` de responsável a uma `String` de localização de sala — um mecanismo não documentado em nenhum ADR/decisão, e que não cobre Professor/Material restritos (só funciona via `Sala.getLocalizacao()`). | [NotificacaoService.java:45-54](../../src/main/java/com/edualoca/domain/notificacao/NotificacaoService.java#L45-L54); [Banco.java:36,92](../../src/main/java/com/edualoca/domain/comum/Banco.java#L92) |
| D-I (já havia sido identificada como RC-4 em §4/§5.3) | Decisão D-2 — manutenção conflitante deve exigir confirmação explícita, não bloquear. | Bloqueia incondicionalmente. | Ver §4 MN-02. |

**Leitura correta destas divergências:** D-A a D-G são consequência natural e esperada do
estágio do projeto (fase de domínio, pré-persistência) — não deveriam ser tratadas como
"bugs", mas **precisam ser explicitamente registradas como dívida de implementação com
prazo**, porque `arquitetura.md` hoje lê como se essas peças já existissem. D-H e D-I são de
natureza diferente: são decisões tomadas **dentro do código**, sem ADR, que podem ou não
coincidir com o que o responsável do projeto decidiria — e no caso de D-I, coincidem
**incorretamente** com uma decisão já registrada e divergente.

---

## 7. Runs de CI consultados (evidência de processo, RNF-12)

Via `gh run list` neste repositório, branch `testes-e-ci`:

| run id | conclusão | duração | observação |
|---|---|---|---|
| 40528622858 | success | 37s | primeiro run verde após `af06798` (fase GREEN) |
| 40528813431 | **failure** | 20s | run proposital vermelho (commit `c902021`, quebra de asserção) |
| 40528932207 | success | 23s | reversão do commit vermelho (`e1ff6e8`) |
| 40529204403 | success | 23s | run mais recente, após `25f5d34` |

O pipeline (`ci.yml`) executa `./mvnw -B verify` (que roda Surefire + Failsafe) e publica
relatórios como artefato — cumpre o requisito mínimo de RNF-12 ("CI executa testes a cada
PR, bloqueando merge em falha"), e o próprio histórico de runs documenta uma validação
deliberada do gate vermelho/verde (commits `c902021`→`e1ff6e8`), o que é uma boa prática de
processo pouco comum em projetos acadêmicos — vale reconhecer como ponto forte, não só
listar riscos. O que falta nesse pipeline (JaCoCo, SonarCloud) já está coberto em D-E (§6).

---

## 8. Ações priorizadas para o próximo ciclo

Ordenadas por impacto nos direcionadores de negócio (§1) e por serem bloqueadoras de outras
ações:

1. **(Direcionador #4 — evidência objetiva) Corrigir `AuditoriaService.consultarPorEntidade`
   para nunca fabricar evento** — deve devolver lista vazia quando não há evento real, e os
   dois testes que dependem da fabricação (`AuditoriaTest.rf27T1`,
   `RequisitosNaoFuncionaisTest.rnf09T1`) devem ser reescritos para criar uma reserva real,
   executar uma transição real, e então consultar a auditoria dela. **Prioridade máxima**:
   sem isso, nenhuma alegação de conformidade com RF-27/RF-29/RNF-09 é confiável.
2. **(Direcionador #4) Reescrever `RegrasCriticasTest.rc03T4`** para efetivamente simular
   falha parcial (ex.: lançar exceção após adquirir lock e antes do `put`, verificar que o
   recurso permanece disponível) — hoje RNF-08 não tem nenhuma evidência real.
3. **(Direcionador #3 — segurança) Corrigir armazenamento de senha**: `Banco.credenciais`
   não deve guardar texto plano; `SenhaHasher` deve migrar de SHA-256 puro para uma função
   com sal/fator de custo (BCrypt, como já citado em RNF-05) antes de qualquer avanço para
   persistência real.
4. **(Direcionador #3) Decidir e corrigir `AutenticacaoService.autenticar`**: hoje não há
   caminho de autenticação real capaz de produzir um perfil diferente de SOLICITANTE —
   definir como o perfil é determinado no login antes de implementar a camada de
   apresentação (Controllers), para não arrastar esse atalho adiante.
5. **(Direcionador #3) Alinhar `CadastroRecursoService.cadastrarManutencao` à decisão D-2**
   — introduzir o parâmetro de confirmação explícita já citado na própria mensagem de erro
   do método, ou reabrir D-2 formalmente se a decisão mudou.
6. **(Direcionador #2/#4) Atualizar `arquitetura.md`** para distinguir claramente "arquitetura
   alvo" de "estado atual implementado" — ao menos uma nota por componente descrito em
   §13–§16 indicando se ele existe hoje (nenhum existe, exceto como serviço de domínio em
   memória) evita que o próximo avaliador/revisor leia o documento como descrição do sistema
   atual.
7. **(Direcionador #1/#4) Planejar a transição de concorrência**: antes de introduzir
   PostgreSQL, decidir explicitamente se o lock em memória (`LOCKS_POR_RECURSO`) será
   substituído pela constraint de banco ou mantido como camada adicional — e, de qualquer
   forma, adicionar o teste de concorrência real contra Testcontainers já exigido
   literalmente pelo enunciado (enunciado.md:69,72), hoje ausente (`RC-03-T3`/`T4` rodam só
   em memória, como os próprios `@DisplayName` admitem).
8. **(Direcionador #4 — entregáveis) Fechar os itens já listados como pendentes no roadmap**
   do próprio README (JaCoCo/SonarCloud, RTM, JMeter) — sem RTM nenhum requisito crítico tem
   rastreabilidade formal ainda, apesar de RNF-11 ser Crítico.
9. **(Direcionador #3, item D-H) Formalizar "responsável competente" por recurso restrito**
   — a solução atual (`responsavelPorLocalizacao`, por string de localização) resolve o
   caso de Sala mas não cobre Professor/Material restritos; decidir o modelo correto antes
   de modelar a entidade `Usuario`/relacionamento em JPA, para não herdar esse atalho na
   migração.

---

*Ciclo 01 conduzido com leitura de `DOCS/enunciado.md`, `DOCS/PERSONAS/*.md`,
`DOCS/REQUISITOS/*.md`, `DOCS/arquitetura.md`, `DOCS/REVISAO_ATAM.md`,
`DOCS/testes/plano-tdd.md`, todo `src/main/java` e `src/test/java`, `pom.xml`,
`.github/workflows/ci.yml`, `README.md` e os runs reais do GitHub Actions via `gh run list`
na branch `testes-e-ci`. Nenhum requisito foi alterado; nenhum código foi modificado como
parte desta avaliação.*
