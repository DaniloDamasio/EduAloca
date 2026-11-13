# EduAloca — Sistema de Alocação de Salas, Professores e Materiais

Sistema para organizar a alocação de salas, professores e materiais escolares, evitando
conflitos de horário e dupla reserva de recursos, com três perfis de acesso:
**Solicitante**, **Responsável** e **Administrador**.

## Autoria e revisão

Trabalho **individual** de Danilo Damasio (QS, 4º semestre). Como não há outro integrante para revisar, a revisão por
pares pedida no enunciado foi substituída por verificações que não dependem do autor, e todas impedem o merge quando
falham:

- **CI obrigatório na `main`:** o check "Build e Testes" é exigido pela proteção da branch (`strict`, `enforce_admins`),
  e nenhum PR entra sem build, suíte completa (unitários, integração com PostgreSQL, E2E em navegador), gate JaCoCo
  (≥ 80% linhas, ≥ 70% ramos) e análise do SonarCloud.
- **Revisão adversarial dos testes:** mutação manual das regras críticas ([DOCS/testes/mutacao-manual.md](DOCS/testes/mutacao-manual.md)),
  que encontrou testes que passavam com qualquer código (DEF-011) e regras sem teste (DEF-014, DEF-015).
- **Relatório de qualidade** com evidência, interpretação e limite para cada afirmação
  ([DOCS/relatorio-qualidade.md](DOCS/relatorio-qualidade.md)).

Limite: nenhuma dessas verificações substitui o olhar de outra pessoa sobre legibilidade e decisões de projeto.

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

## Executando a Aplicação e a Interface Web

Pré-requisito: JDK 21. O modo padrão guarda os dados em memória e não precisa de banco nem de Docker.

```
./mvnw -B package -DskipTests
java -jar target/edualoca-0.0.1-SNAPSHOT.jar
```

Abra <http://localhost:8080/>. A interface (HTML, CSS e JavaScript puros, sem dependências) é servida pela própria
aplicação a partir de [src/main/resources/static](src/main/resources/static), adapta-se a celular e desktop e consome
só a API pública documentada em **[DOCS/api.md](DOCS/api.md)**. No modo padrão (em memória) os dados voltam ao seed a cada reinício; com
PostgreSQL, catálogo e reservas são mantidos (ver "Escopo da persistência" e o [ADR-010](DOCS/adr/ADR-010-escopo-da-persistencia-postgresql.md)).

### Escopo da persistência (PostgreSQL)

O PostgreSQL guarda o que as regras críticas do enunciado exigem que seja verificado em ambiente real: as **reservas**,
os **recursos de cada reserva** e o **histórico de estados**. É no banco que fica a garantia de "duas solicitações
simultâneas produzem uma única reserva aceita": a constraint de exclusão `ex_reserva_recurso_sem_sobreposicao`
([ADR-009](DOCS/adr/ADR-009-exclusao-gist-tstzrange.md)) recusa sobreposição mesmo com duas instâncias da aplicação. O
histórico é gravado na mesma transação da mudança de estado e um trigger recusa `UPDATE`/`DELETE`. Tudo isso é testado
contra PostgreSQL real com Testcontainers (`ConcorrenciaReservaIT`, `ConcorrenciaReservaMultiInstanciaIT`,
`ReservaRepositorioJdbcIT`, `HistoricoReservaTransacaoIT`) e foi medido sob carga ([resultado-02](DOCS/performance/resultado-02.md)).

O **catálogo** (salas, professores com competências e materiais) também é gravado no banco a cada cadastro, edição e
inativação. Na subida, a aplicação recarrega do PostgreSQL o catálogo e as reservas, e o histórico continua sendo lido do
banco. Assim, **com o PostgreSQL ligado, um reinício não perde o que foi cadastrado nem as reservas**
(`PersistenciaReinicioIT`, `CatalogoRepositorioJdbcIT`). As leituras continuam saindo de um cache em memória preenchido
pelo banco, então o domínio e as regras de concorrência não mudaram.

Continuam **só em memória**, e voltam ao seed num reinício: manutenções, usuários e senhas, auditoria, retiradas,
notificações, tokens e tentativas de login ([ADR-010](DOCS/adr/ADR-010-escopo-da-persistencia-postgresql.md), seção
"Atualização"). Com duas instâncias, cada uma só enxerga o catálogo gravado pela outra depois de reiniciar.

Para rodar com PostgreSQL (o modo padrão, sem essas propriedades, é em memória):

```
docker run -d --name edualoca-pg -e POSTGRES_DB=edualoca -e POSTGRES_USER=edualoca \
  -e POSTGRES_PASSWORD=<senha> -p 127.0.0.1:5432:5432 postgres:16-alpine
java -jar target/edualoca-0.0.1-SNAPSHOT.jar \
  --edualoca.persistencia.url=jdbc:postgresql://localhost:5432/edualoca \
  --edualoca.persistencia.usuario=edualoca --edualoca.persistencia.senha=<senha>
```

O esquema é criado na subida (`db/postgres/schema.sql`, idempotente). Reiniciar o `java -jar` mantém o catálogo e as
reservas.

### Contas de demonstração

A tela de login tem um botão para cada conta. As senhas são de seed e servem só para teste e demonstração.

| Perfil | E-mail | Senha | O que vê na interface |
|---|---|---|---|
| Solicitante | `usuario@example.com` | `senhaValida123` | Buscar e reservar; Minhas reservas (alterar horário, cancelar inclusive em uso); Catálogo |
| Solicitante (2º) | `novo@example.com` | `minhaSenha123` | Idem, para mostrar que um não vê a reserva do outro |
| Responsável (Bloco A) | `responsavel@example.com` | `senhaResponsavel123` | Aprovações e uso (aprovar, rejeitar, iniciar uso, não compareceu, concluir, retirada/devolução, histórico, devoluções em atraso); Buscar disponibilidade; Relatório; Catálogo |
| Responsável (Bloco B) | `responsavel.b@example.com` | `senhaResponsavelB123` | Idem, só para reservas com sala do Bloco B |
| Administrador | `admin@example.com` | `senhaAdmin123` | Tudo do Responsável, em qualquer bloco, mais Cadastro e manutenção (criar, editar, inativar, bloquear com confirmação, lista de manutenções), Usuários e Auditoria |

### Roteiro de demonstração (cerca de 5 minutos)

1. **Administrador** → *Cadastro e manutenção*: cadastre a sala "Lab de Robótica" no Bloco A marcada como **restrita** e o
   material "Kit Arduino" (5 unidades). O seed não tem recurso restrito, então é esse passo que habilita o fluxo de aprovação.
2. **Solicitante** → *Buscar e reservar*: busque salas do Bloco A, adicione o Lab de Robótica; troque o tipo para
   Material, busque `kit` e adicione 2 unidades; informe a finalidade e solicite. A reserva nasce **Solicitada**.
3. Ainda como Solicitante, tente reservar a mesma sala no mesmo horário: a interface mostra o 409 de sobreposição.
   Tente também fim antes do início (400).
4. **Responsável (Bloco B)**: a lista vem vazia, porque a sala é do Bloco A (competência por local).
5. **Responsável (Bloco A)** → *Aprovações e uso*: aprove, troque o filtro para *Todos*, **inicie o uso**, abra
   *Retirada e devolução*, registre a retirada do kit e depois a devolução; **conclua** a reserva e abra o *Histórico*
   (Solicitada → Aprovada → Em uso → Concluída). *Não compareceu* só é aceito a partir de 15 minutos depois do início.
6. **Responsável** → *Relatório*: horas por recurso, carga por professor e conflitos evitados no período.
7. **Administrador** → *Bloquear para manutenção*: bloqueie uma sala num horário; como Solicitante, ela some da busca
   nesse horário. Se houver reserva aprovada no período, a tela pede **confirmação explícita** (RF-23): confirmar cancela
   essas reservas e avisa os solicitantes; reserva já em uso impede a manutenção.
8. **Administrador** → *Usuários*: cadastre um Responsável para o "Bloco C"; saia e entre com essa conta. Ela só vê e
   decide reservas de salas do Bloco C. Inative a conta e o acesso é encerrado na hora.
9. **Administrador** → *Auditoria*: filtre por entidade (Reserva, Sala, Usuário…) e veja quem fez cada mudança.
10. **Não comparecimento automático:** uma reserva aprovada que chega ao fim do horário sem início de uso vira
    *Não compareceu* sozinha (tarefa a cada 60 s; o histórico mostra o autor "sistema"). Para ver ao vivo, crie uma
    reserva que termine em poucos minutos.

### Rotas usadas pela interface

| Rota | Perfis | Observação |
|---|---|---|
| `POST /auth/login`, `POST /auth/logout`, `GET /auth/eu` | todos | `eu` devolve id, e-mail e perfil do token; `logout` revoga o token (204) |
| `GET /recursos` | todos | catálogo ativo: salas, professores, materiais |
| `GET /salas/disponiveis`, `GET /professores/disponiveis`, `GET /materiais/disponiveis` | todos | busca por tipo, capacidade, localização, competência, quantidade e horário |
| `GET /reservas` | todos | Solicitante: as próprias; Responsável: as do seu bloco; Administrador: todas |
| `POST /reservas`, `PUT /reservas/{id}`, `POST /reservas/{id}/cancelamento` | Solicitante (dono) | criar, alterar horário, cancelar |
| `POST /reservas/{id}/aprovacao`, `/rejeicao`, `/inicio-uso`, `/conclusao`, `/nao-comparecimento`, `GET /reservas/{id}/historico` | Responsável competente, Administrador | não comparecimento só a partir de início + 15 min |
| `GET`/`POST /reservas/{id}/retiradas`, `POST /retiradas/{id}/devolucao` | Responsável competente, Administrador | retirada só com reserva `EM_USO`; condição `INTEGRO` ou `DANIFICADO` |
| `GET /relatorios/utilizacao` | Responsável, Administrador | ver seção do relatório |
| `GET /retiradas/pendentes` | Responsável competente, Administrador | devoluções em atraso (RF-26) |
| `POST /salas`, `/professores`, `/materiais`, `PUT /{salas\|professores\|materiais}/{id}`, `POST /{salas\|professores\|materiais}/{id}/inativacao` | Administrador | edição no lugar; identificador do professor não muda |
| `GET`/`POST /manutencoes`, `POST /manutencoes/{id}/encerramento` | Administrador | `confirmarColisao: true` confirma a manutenção sobre reserva aprovada (RF-23); encerrar libera o recurso na hora |
| `GET`/`POST /usuarios`, `POST /usuarios/{id}/inativacao`, `GET /auditoria` | Administrador | gestão de contas e consulta da auditoria (somente leitura) |

Referência completa (corpos, respostas, códigos de erro e o teste de cada rota): **[DOCS/api.md](DOCS/api.md)**.
Especificação OpenAPI 3 com exemplos: [src/main/resources/static/openapi.yaml](src/main/resources/static/openapi.yaml),
servida em <http://localhost:8080/openapi.yaml> e mantida em sincronia com o código por `DocumentacaoOpenApiTest`
(importe o arquivo no [Swagger Editor](https://editor.swagger.io) para navegar).
Testes das rotas acima: [InterfacePerfisApiTest](src/test/java/com/edualoca/InterfacePerfisApiTest.java) e
[AdministracaoApiTest](src/test/java/com/edualoca/AdministracaoApiTest.java) (caixa-preta, token do login real), além de
[MatrizAutorizacaoApiTest](src/test/java/com/edualoca/MatrizAutorizacaoApiTest.java).

## Executando a Suíte Localmente

```
./mvnw -B verify
```

Isso roda toda a suíte (unitária e de integração) e o gate de cobertura do JaCoCo
(≥80% linha, ≥70% branch). Os testes de integração com o provedor de notificação externa
([IntegracaoNotificacaoExternaTest](src/test/java/com/edualoca/IntegracaoNotificacaoExternaTest.java))
sobem um servidor WireMock em porta dinâmica — não dependem de rede externa nem de
nenhuma credencial real, e rodam de forma determinística em qualquer máquina.

Os 4 testes de integração com PostgreSQL (`*IT`, Testcontainers) precisam de um Docker acessível. Com **Colima** no
macOS, o Testcontainers não encontra o socket sozinho e os ITs falham com "Could not find a valid Docker
environment". Nesse caso:

```
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./mvnw -B verify
```

Com Docker Desktop ou no CI (`ubuntu-latest`), basta `./mvnw -B verify`.

O teste end-to-end [FluxoCompletoNavegadorIT](src/test/java/com/edualoca/FluxoCompletoNavegadorIT.java) abre a interface
real em Chromium headless (Playwright 1.64.0) e percorre o fluxo dos três perfis, do cadastro à conclusão, além de
conferir o layout em 375 px. Na primeira execução, o Playwright baixa o navegador para o cache do usuário. No CI, o
passo `npx playwright@1.64.0 install --with-deps chromium` faz isso antes do `verify`.

## Teste de carga (JMeter)

Plano e requisitos: [DOCS/performance/rnf.md](DOCS/performance/rnf.md). **Resultado vigente:
[DOCS/performance/resultado-02.md](DOCS/performance/resultado-02.md)**: até 400 usuários, 0 duplas reservas em 6.308
janelas disputadas, 0 erros, p95 de criação de 8–12 ms e sem saturação. A rodada é local e manual (não roda no CI) e
exige Docker e JMeter 5.6.3:

```
./perf/executar-rodada-02.sh .
```

O script compila o commit atual (e se recusa a rodar com alterações em `src/`, `pom.xml` ou no plano), recria
PostgreSQL e aplicação a cada cenário e grava os brutos em `perf/resultados/rodada-02/`, que não são versionados. Ele
também marca o cenário como inválido se a máquina suspender: mantenha o notebook na tomada e com a tampa aberta.

### Mutação automatizada (PIT)

```
./mvnw -B -Pmutacao test-compile org.pitest:pitest-maven:mutationCoverage
```

Muta o pacote `domain` e roda a suíte sem os ITs (cerca de 5 minutos; fora do `verify` e do CI). Relatório em
`target/pit-reports/index.html`; resultado e análise dos sobreviventes em [DOCS/testes/mutacao-pit.md](DOCS/testes/mutacao-pit.md).

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

## API — Relatório de utilização (RF-34, RF-35, RF-36)

`GET /relatorios/utilizacao?inicio=<aaaa-mm-ddThh:mm>&fim=<aaaa-mm-ddThh:mm>` — somente leitura.

```
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/relatorios/utilizacao?inicio=2027-03-01T00:00&fim=2027-04-01T00:00"
```

```json
{
  "recursos": [
    { "tipo": "SALA", "id": "6f0c…", "nome": "Sala 101", "horasReservadas": 4.00 },
    { "tipo": "SALA", "id": "a41b…", "nome": "Lab 2",    "horasReservadas": 1.50 }
  ],
  "professores": [
    { "id": "c9d2…", "nome": "Prof. Ana", "cargaHorariaAlocada": 3.50 }
  ],
  "conflitosEvitados": 2
}
```

Regras do cálculo:

- **Período semiaberto `[inicio, fim)`**, no horário local do servidor, como o resto da API. Máximo de 366 dias.
- **Uso** = reservas `APROVADA`, `EM_USO` ou `CONCLUIDA`. `SOLICITADA` (pendente), `REJEITADA`, `CANCELADA` e
  `NAO_COMPARECEU` não contam.
- Reserva que **cruza a borda** do período conta só a parte que cai dentro dele.
- `recursos` traz salas e materiais (horas da reserva, independente da quantidade de itens do material);
  `professores` traz a carga horária alocada a cada professor. Só aparece quem tem uso no período; a ordem é
  maior número de horas primeiro e, no empate, nome.
- Horas em decimal com 2 casas (90 min = `1.50`), medidas como tempo decorrido no fuso do servidor: uma reserva
  que atravessa mudança de horário de verão conta as horas reais, não a diferença do relógio de parede.
- **`conflitosEvitados`** = tentativas de criar ou alterar reserva recusadas por sobreposição com reserva ativa
  (checagem do serviço ou constraint `ex_reserva_recurso_sem_sobreposicao`), contadas pelo **instante da
  tentativa** dentro do período (critério de aceite de RF-35: rejeições registradas em auditoria no período). Cada
  tentativa grava um evento `REJEICAO_SOBREPOSICAO` na auditoria. Recusa por manutenção não conta.

| Status | Quando |
|---|---|
| 200 | Responsável ou Administrador com período válido (período sem dados devolve listas vazias e `0`) |
| 400 | `inicio`/`fim` ausente, fora do formato ISO, `fim <= inicio` ou período maior que 366 dias |
| 401 | Sem token ou token desconhecido |
| 403 | Solicitante (checado antes dos parâmetros: o Solicitante recebe 403 mesmo com período inválido) |

Testes: [RelatorioUtilizacaoApiTest](src/test/java/com/edualoca/RelatorioUtilizacaoApiTest.java) (caixa-preta, massa
semeada pela própria API e valores calculados à mão no comentário de cada teste) e
[RejeicaoSobreposicaoAuditoriaTest](src/test/java/com/edualoca/RejeicaoSobreposicaoAuditoriaTest.java).

**Limitações conhecidas:** o relatório lê o cache em memória das reservas (`Banco.reservas`) e a auditoria em
memória; com PostgreSQL, reservas e conflitos anteriores a um reinício da aplicação não aparecem (pendência P2 de
[DOCS/status-requisitos.md](DOCS/status-requisitos.md)). Os eventos de auditoria usam horário local sem fuso.

## Nota de segurança da API HTTP

Camada HTTP em `com.edualoca.api` (reservas, manutenção, disponibilidade de salas).

- **Login e senhas (RF-01, RNF-05, DEF-013):** `POST /auth/login` troca e-mail e senha por um token. A senha
  nunca é guardada em claro: `Banco.credenciais` guarda só `pbkdf2-sha256$600000$<sal>$<hash>` (PBKDF2-HMAC-SHA256
  do JDK, sal aleatório de 16 bytes por conta). A conferência compara em tempo constante e, para e-mail
  inexistente, faz a mesma derivação contra um hash fictício, de modo que a resposta (401, mesma mensagem) e o tempo
  não revelam se a conta existe. As contas de demonstração do seed têm senha fixa no código: servem para teste e
  demonstração, não para implantação.
- **Autenticação:** toda rota exige `Authorization: Bearer <token>`. O token é opaco, gerado
  com `SecureRandom` (256 bits) e resolvido no servidor (`TokenService`). Sem token ou com
  token desconhecido, a resposta é 401. O token nunca vai na URL.
- **Autorização (RF-02, RF-03, RF-19):** o ator é sempre o usuário do token, nunca um campo
  do corpo. Perfil e dono são checados no domínio (`ReservaService`). `POST /manutencoes`
  é exclusivo do Administrador.
- **Histórico auditável das reservas (RF-27, RF-29, RF-30):** `GET /reservas/{id}/historico`
  é só leitura e só atende Responsável e Administrador. O Solicitante recebe 403 mesmo sendo
  o dono, e o perfil é checado antes da existência da reserva, para não revelar quais ids existem.
  Não há rota de escrita (PUT/PATCH/DELETE/POST → 405). No PostgreSQL, a mudança de estado e o
  registro de histórico são gravados na mesma transação, e um trigger recusa UPDATE/DELETE em
  `reserva_historico`. O usuário e o instante do registro vêm do token e do `Clock` do servidor,
  nunca do corpo da requisição. A recusa por trigger protege contra a aplicação, não contra um
  DBA com privilégio para desabilitar o trigger.
- **Relatórios (RF-36):** `GET /relatorios/utilizacao` só atende Responsável e Administrador; o perfil é checado
  antes dos parâmetros, então o Solicitante recebe 403 sem nenhum dado e sem eco da entrada. O período é limitado a
  366 dias para conter o custo da consulta.
- **Entrada (RNF-04):** JSON malformado, data fora do formato ISO, tipo/id de recurso
  inexistente, quantidade < 1 ou mais de 50 recursos por reserva resultam em 400.
- **Erros (RF-38, RNF-06, DEF-002):** `application/problem+json` (ProblemDetail, RFC 9457), sem stacktrace, nome
  de classe ou mensagem de framework. Erro inesperado vira 500 genérico; o detalhe fica só no log.
- **Cadastro, início de uso e retirada:** cadastro e inativação de sala, professor e material são exclusivos do
  Administrador (checagem na borda, `Perfis.exigir`, antes de ler o corpo). Textos têm no máximo 120 caracteres e um
  professor tem no máximo 20 competências. Início de uso e retirada/devolução aceitam só Responsável competente pelo
  local ou Administrador. Reserva alheia ou inexistente recebe o mesmo 403. Data e autor da retirada vêm do servidor e
  do token.
- **Interface web:** a CSP (`CabecalhosSegurancaFiltro`) só aceita script, estilo e conexão da própria origem, e a
  interface não tem script inline. Todo dado da API entra na página por `textContent`, nunca por `innerHTML`.
  `X-Frame-Options: DENY` e `frame-ancestors 'none'` impedem clickjacking. O token fica em `sessionStorage` e é
  revogado no servidor ao sair (`POST /auth/logout`).
- **Sessão e força bruta (RNF-05):** o token vence após 30 min sem uso ou 8 h desde o login (`401` "Sessão expirada";
  a interface volta ao login), configurável por `edualoca.seguranca.token.inatividade-minutos` e
  `edualoca.seguranca.token.duracao-maxima-horas`. Depois de 5 senhas erradas para o mesmo e-mail em 15 min, o login
  desse e-mail fica bloqueado por 15 min (`429`), inclusive com a senha certa e igual para e-mail inexistente, para não
  revelar quais contas existem (`SegurancaLoginTest`, `LoginBloqueioApiTest`).
- **Limitações conhecidas:** tokens e contadores de tentativa ficam em memória (perdidos ao reiniciar, como o resto do
  seed; ver "Escopo da persistência"). O bloqueio é por e-mail, não por IP: um atacante pode bloquear temporariamente o
  login de uma conta conhecida.
  Pela API, toda escrita de cadastro, manutenção, usuário, retirada e devolução grava na auditoria o usuário do token
  como autor (RF-28); só chamadas diretas ao domínio sem ator (testes antigos) gravam autor `null`. Usuários ficam em
  memória como o resto do seed: inativação não tem reativação (o e-mail fica bloqueado) e não há troca de senha pela
  interface. O material é exclusivo no intervalo, na busca e na criação: uma reserva ativa sobreposta tira o material
  da busca, qualquer que seja a quantidade ([DEF-016](DOCS/defeitos/DEF-016-busca-e-criacao-divergem-na-quantidade-de-material.md),
  corrigido pela opção A). A notificação
  externa só é chamada com `edualoca.notificacao.externa.habilitada=true`; a chave vem da variável de ambiente
  `EDUALOCA_NOTIFICACAO_API_KEY`, nunca de arquivo versionado.

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
- [x] Testes de integração com persistência real (Testcontainers) e concorrência entre instâncias
- [x] Teste de carga (JMeter): resultado vigente na **rodada 02** ([DOCS/performance/resultado-02.md](DOCS/performance/resultado-02.md)); rodada 01 como histórico ([resultado-01.md](DOCS/performance/resultado-01.md))
- [x] Interface web responsiva para os três perfis (HTML, CSS e JS puros em `src/main/resources/static`)
- [x] RTM (Requirements Traceability Matrix), 32/32 requisitos críticos, ver [DOCS/RTM.md](DOCS/RTM.md)
- [x] Notificação ligada aos fluxos de reserva (simulada por padrão; adaptador HTTP opcional)
- [x] Ciclo completo de estados: concluir e não comparecimento (P-05/P-07/P-08)
- [x] Teste end-to-end em navegador real (Playwright)
- [x] Documentação da API ([DOCS/api.md](DOCS/api.md)) e escopo da persistência ([ADR-010](DOCS/adr/ADR-010-escopo-da-persistencia-postgresql.md))
- [x] OpenAPI 3 das 39 operações, com teste de sincronia (RF-39)
- [x] Gestão de usuários, consulta de auditoria, lista de manutenções e confirmação explícita (RF-23)
- [x] Não comparecimento automático no fim do horário (P-08) e devoluções em atraso (RF-26)
- [x] Roteiro de apresentação e demonstração ([DOCS/apresentacao.md](DOCS/apresentacao.md))
- [x] Mutação automatizada com PIT no domínio: 90,6% dos mutantes mortos ([DOCS/testes/mutacao-pit.md](DOCS/testes/mutacao-pit.md))
