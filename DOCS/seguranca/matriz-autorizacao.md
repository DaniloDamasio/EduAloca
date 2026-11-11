# Matriz de autorização da API

Levantamento de 2026-11-11, branch `relatorio-utilizacao` (commit `50db94f`). Este documento registra a resposta
**esperada** de cada operação por perfil e marca onde o código atual diverge. Nenhuma correção foi feita.

## 1. Como a autorização funciona hoje

- **Não há Spring Security no projeto.** O `pom.xml` não traz `spring-boot-starter-security` e não existe
  `SecurityFilterChain`, `@PreAuthorize` nem filtro ou interceptor de autenticação.
- A autenticação depende do handler: o parâmetro anotado com `@UsuarioAutenticado` é resolvido por
  [UsuarioAutenticadoResolver](../../src/main/java/com/edualoca/api/seguranca/UsuarioAutenticadoResolver.java), que
  chama `TokenService.autenticar` e lança 401 sem token válido. **Rota sem esse parâmetro é pública.**
- O perfil e a posse são checados em pontos diferentes: no controller
  ([ManutencaoController](../../src/main/java/com/edualoca/api/recurso/ManutencaoController.java)) ou no serviço
  ([ReservaService](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java),
  [RelatorioService](../../src/main/java/com/edualoca/domain/relatorio/RelatorioService.java)).

## 2. Regras de posse

| ID | Regra | Fonte |
|---|---|---|
| P-1 | O Solicitante só consulta, altera e cancela **as próprias** reservas. Em reserva de terceiro, recebe 403, inclusive quando o id não existe, para não revelar quais ids existem. | RF-03, RF-13, RF-14 |
| P-2 | O **Responsável** aprova reserva de recurso restrito só para recursos sob sua responsabilidade (Responsável competente). O **Administrador** também aprova (RF-19). A rejeição segue a mesma regra, por ser a outra metade da mesma decisão. | Regra "Somente responsáveis aprovam recursos restritos" (enunciado #7), RF-18, RF-19, RF-32 |
| P-3 | Reserva não é apagada fisicamente por nenhum perfil. O fim de uma reserva é o cancelamento. | RF-30 |

> **Conflito de fonte (resolvido em 11/11/2026):** o RF-19 diz "Responsável/Administrador aprovam ou rejeitam" e o
> levantamento inicial seguia a leitura estrita do enunciado (só o Responsável). Decisão: **manter o RF-19**. O
> Administrador aprova e rejeita, a D-04 deixa de ser divergência e o cenário BDD-R1-05 continua valendo.

## 3. Matriz

Legenda: **200/201/204** = permitido · **401** = sem token válido · **403** = perfil ou posse não autorizados.
"própria/alheia" aplica a regra de posse P-1. A coluna **Situação** compara a resposta esperada com o código atual.

| # | Operação | Rota | Anônimo | Solicitante | Responsável | Administrador | Situação |
|---|---|---|---|---|---|---|---|
| 1 | Login | `POST /auth/login` | 200 | 200 | 200 | 200 | ⚠️ **D-01** |
| 2 | Buscar salas disponíveis | `GET /salas/disponiveis` | 401 | 200 | 200 | 200 | ✅ |
| 3 | Criar reserva | `POST /reservas` | 401 | 201 | 403 | 403 | ⚠️ **D-03** |
| 4 | Consultar reserva | `GET /reservas/{id}` | 401 | 200 própria · 403 alheia | 200 | 200 | ✅ |
| 5 | Alterar reserva (RF-13) | `PUT /reservas/{id}` | 401 | 200 própria · 403 alheia | 403 | 403 | ⚠️ **D-02** |
| 6 | Cancelar reserva | `POST /reservas/{id}/cancelamento` | 401 | 200 própria · 403 alheia | 403 | 403 | ⚠️ **D-07**, ver nota |
| 7 | Aprovar reserva restrita | `POST /reservas/{id}/aprovacao` | 401 | 403 | 200 se competente · 403 se não | 200 | ⚠️ **D-05** |
| 8 | Rejeitar reserva restrita | `POST /reservas/{id}/rejeicao` | 401 | 403 | 200 se competente · 403 se não | 200 | ⚠️ **D-05** |
| 9 | Histórico auditável da reserva | `GET /reservas/{id}/historico` | 401 | 403 | 200 | 200 | ✅ |
| 10 | Excluir reserva | `DELETE /reservas/{id}` | 401 | 403 | 403 | 403 | ⚠️ **D-06** |
| 11 | Cadastrar manutenção/bloqueio | `POST /manutencoes` | 401 | 403 | 403 | 201 | ✅ |
| 12 | Relatório de utilização | `GET /relatorios/utilizacao` | 401 | 403 | 200 | 200 | ✅ |

Nota da linha 6: o código checa só a posse, sem olhar o perfil. Isso produz a resposta esperada enquanto Responsável
e Administrador não tiverem reservas próprias. Se a D-03 for resolvida permitindo que eles criem reservas, a linha 6
passa a valer "200 própria · 403 alheia" para os três perfis, e o código já se comporta assim.

### Evidência

- Anônimo e Solicitante: respostas obtidas com a aplicação em execução (`java -jar`, perfil padrão em memória), com
  login real (`usuario@example.com` e `novo@example.com`) e uma reserva criada pelo primeiro usuário. Todas as
  respostas observadas aparecem nas divergências abaixo ou coincidem com a matriz.
- Responsável e Administrador: **não é possível obter esses tokens pela API** (ver D-01). As colunas foram conferidas
  por leitura do código e pelos testes que injetam o token direto no `TokenService` (`ApiSegurancaTest`,
  `HistoricoReservaApiTest`, `RelatorioUtilizacaoApiTest`).

## 4. Divergências encontradas

| ID | Linha | Esperado | Código atual | Onde |
|---|---|---|---|---|
| D-01 | 1 | O login emite token com o perfil do usuário cadastrado. | `AutenticacaoService.autenticar` devolve sempre `Perfil.SOLICITANTE`. Pela API, nenhum usuário chega a Responsável ou Administrador, então as operações 7, 8, 9, 11 e 12 só funcionam em teste. | [AutenticacaoService.java:17](../../src/main/java/com/edualoca/domain/usuario/AutenticacaoService.java#L17) |
| D-02 | 5 | Alteração exposta, com 401 para anônimo e posse aplicada. | Não há rota de alteração. `PUT` e `PATCH /reservas/{id}` respondem **405 para todos, inclusive anônimo**. A regra de posse existe em `ReservaService.alterar`, mas nenhum endpoint a usa. | [ReservaController.java](../../src/main/java/com/edualoca/api/reserva/ReservaController.java), [ReservaService.java:83-92](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L83-L92) |
| D-03 | 3 | Só o Solicitante cria reserva (RF-12: perfil SOL). | Qualquer usuário autenticado cria: não há checagem de perfil. | [ReservaController.java:39-44](../../src/main/java/com/edualoca/api/reserva/ReservaController.java#L39-L44) |
| ~~D-04~~ | 7, 8 | ~~Administrador recebe 403.~~ **Encerrada:** RF-19 mantido, o Administrador aprova (200). | Administrador aprova e rejeita (200), de acordo com o RF-19. | [ReservaService.java:143](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L143), [ReservaService.java:157](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L157) |
| D-05 | 7, 8 | Só o Responsável competente pelo recurso decide. | Qualquer Responsável aprova ou rejeita reserva de qualquer recurso. O vínculo responsável↔recurso existe (`Banco.responsavelPorLocalizacao`), mas só a notificação o usa. | [ReservaService.java:141-170](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L141-L170), [NotificacaoService.java:50](../../src/main/java/com/edualoca/domain/notificacao/NotificacaoService.java#L50) |
| D-06 | 10 | 403 para qualquer perfil autenticado. | 403 para reserva iniciada ou inexistente, mas **405** para reserva em `SOLICITADA`, numa rota que existe. O controle de acesso está certo (ninguém apaga); a divergência é de status. O RF-30 aceita 403 ou 405, mas 405 numa rota mapeada contradiz o significado do código. | [ReservaService.java:200-207](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L200-L207) |

| D-07 | 6 | 403 para id alheio **e** para id inexistente (P-1). | Id inexistente responde `409 Reserva inexistente`; comparando com o 403 do id alheio, o Solicitante descobre quais ids existem. Achada pela execução da matriz (`MatrizAutorizacaoApiTest`), não pelo levantamento. | [ReservaService.java:125-131](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L125-L131) |

### Execução automatizada e defeitos

A matriz é executada célula a célula por `MatrizAutorizacaoApiTest` (MockMvc, tokens obtidos pelo login real). Cada
célula vermelha foi registrada em `DOCS/defeitos/` antes da correção:

| Divergência | Defeito |
|---|---|
| D-01 | [DEF-003](../defeitos/DEF-003-login-emite-sempre-solicitante.md) |
| D-02 | [DEF-004](../defeitos/DEF-004-alteracao-de-reserva-sem-rota.md) |
| D-06 | [DEF-006](../defeitos/DEF-006-exclusao-de-solicitada-responde-405.md) |
| D-07 | [DEF-005](../defeitos/DEF-005-cancelamento-revela-id-inexistente.md) |
| Erros fora de ProblemDetail (todas as células de erro) | [DEF-002](../defeitos/DEF-002-erros-fora-do-padrao-problemdetail.md) |
| Validação de entrada (fora da matriz) | [DEF-007](../defeitos/DEF-007-capacidade-minima-negativa-aceita.md), [DEF-008](../defeitos/DEF-008-parametro-ausente-sem-nome-do-campo.md) |
| D-03, D-05 | Mascaradas pelo D-01: as células falham no login antes de chegar à operação. Registro após a correção do DEF-003. |

## 5. Rotas que não aparecem em nenhuma regra de segurança

Como não existe configuração central, "regra de segurança" aqui significa o `@UsuarioAutenticado` no handler mais
uma checagem de perfil ou de posse. Rotas fora disso:

| Rota | Quem acessa hoje | Observação |
|---|---|---|
| `POST /auth/login` | Anônimo | Pública por design, mas só porque não pede `@UsuarioAutenticado`. Nenhum lugar declara que ela é pública. |
| `/error` (qualquer método) | Anônimo | Rota do Spring Boot (`BasicErrorController`). `GET /error` respondeu 500 com `{"status":999,"error":"None"}`. Não passa pelo `TratadorDeErros`. |
| `PUT`/`PATCH /reservas/{id}` e qualquer método não mapeado | Anônimo | O Spring responde 405 antes de qualquer autenticação (ver D-02). |
| `OPTIONS` em todas as rotas | Anônimo | Tratado pelo framework; respondeu 200 sem token. |
| `GET /salas/disponiveis` | Qualquer autenticado | Só autenticação, sem perfil. Está de acordo com a matriz (RF-07/RF-08). |
| `POST /reservas` | Qualquer autenticado | Só autenticação, sem perfil (D-03). |
| `POST /reservas/{id}/cancelamento` | Qualquer autenticado | Só posse, sem perfil (nota da linha 6). |

**Risco estrutural:** a proteção é *opt-in*. Um controller novo que esqueça o parâmetro `@UsuarioAutenticado`
fica público sem nenhum aviso, e a ordem 401 → 403 → 400 depende de o parâmetro vir antes do `@RequestBody`
em cada método. Uma regra central que negue por padrão (filtro ou Spring Security) fecharia as duas brechas.

## 6. Operações previstas nos requisitos e sem rota

Não entram na matriz porque não existem na API, mas valem como pendência de autorização para quando forem expostas:
CRUD de salas, professores, materiais e usuários (RF-04 a RF-06, só Administrador), busca de professores e
materiais (RF-09, RF-10), retirada e devolução de material (RF-24, RF-25, Responsável).

## 7. Fora do escopo da matriz, mas notado

- As senhas do seed ficam em texto plano em `Banco.credenciais` e são comparadas com `equals`
  ([AutenticacaoService.java:12-13](../../src/main/java/com/edualoca/domain/usuario/AutenticacaoService.java#L12-L13)),
  contra o RNF-05.
- Os tokens ficam em memória e não expiram, e não há logout
  ([TokenService.java](../../src/main/java/com/edualoca/api/seguranca/TokenService.java)).
