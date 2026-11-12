# API HTTP do EduAloca

Referência das rotas públicas da aplicação (RF-14 do enunciado). Vale para o commit em que este arquivo foi atualizado.
A fonte de verdade são os controllers em `src/main/java/com/edualoca/api/`. Os testes de API que exercitam cada rota estão
na coluna "Teste".

A mesma API está descrita em **OpenAPI 3** em [`src/main/resources/static/openapi.yaml`](../src/main/resources/static/openapi.yaml),
servido pela aplicação em `/openapi.yaml` (sem token), com parâmetros, corpos, respostas e exemplos de cada operação.
O teste `DocumentacaoOpenApiTest` compara esse arquivo com as rotas registradas no Spring MVC e falha se alguma rota
ficar sem documentação ou se houver documentação de rota inexistente (RF-39).

## Convenções

- **Base:** `http://localhost:8080` (porta padrão do Spring Boot). A interface web é servida na mesma origem, em `/`.
- **Autenticação:** todas as rotas, exceto `POST /auth/login`, exigem `Authorization: Bearer <token>`. O token sai do
  login, é opaco (256 bits de `SecureRandom`) e vive em memória até o logout. Sem token, ou com token desconhecido: `401`.
- **Perfis:** `SOL` = Solicitante, `RES` = Responsável, `ADM` = Administrador. "RES competente" é o Responsável do
  local (bloco) da sala da reserva; o Administrador decide qualquer reserva.
- **Datas:** `LocalDateTime` ISO-8601 sem fuso (`2027-03-10T08:00`), interpretadas no fuso do servidor. Intervalos são
  semiabertos: `[inicio, fim)`. Reservas que só encostam não se sobrepõem.
- **Erros:** `application/problem+json` (RFC 9457) com `type`, `title`, `status` e `detail` em português, sem stack trace,
  SQL ou nome de classe (RF-38, RNF-06). Códigos usados:

| Código | Quando |
|---|---|
| `400` | Entrada inválida: JSON malformado, campo obrigatório ausente (o `detail` nomeia o campo), data fora do formato, fim ≤ início, número fora do limite |
| `401` | Sem token ou token desconhecido; credencial inválida no login (mesma mensagem para e-mail inexistente e senha errada) |
| `403` | Perfil fora do escopo, Responsável de outro local ou reserva de outro Solicitante. Id inexistente recebe o mesmo `403` que id alheio, para não revelar quais ids existem |
| `404` | Cadastro inexistente na edição de sala, professor ou material (só o Administrador chega a essa checagem) |
| `405` | Método não suportado pela rota |
| `409` | Conflito de regra: sobreposição de horário, recurso em manutenção, estado da reserva incompatível com a ação, retirada com ciclo aberto, não comparecimento antes da tolerância |

## Autenticação

| Método e rota | Perfil | Corpo | Resposta | Teste |
|---|---|---|---|---|
| `POST /auth/login` | público | `{"email", "senha"}` | `200 {"token"}`; `401` credencial inválida; `400` campo vazio | `LoginApiTest`, `MatrizAutorizacaoApiTest` |
| `POST /auth/logout` | qualquer | — | `204`, mesmo sem token válido (não revela quais tokens existem) | `InterfacePerfisApiTest` |
| `GET /auth/eu` | autenticado | — | `200 {"id", "email", "perfil"}` | `InterfacePerfisApiTest` |

Contas de demonstração (seed em memória, senhas guardadas com PBKDF2): `usuario@example.com` / `novo@example.com`
(SOL), `responsavel@example.com` (RES, Bloco A), `responsavel.b@example.com` (RES, Bloco B), `admin@example.com` (ADM).
As senhas estão no [README](../README.md).

## Cadastro e catálogo (RF-04, RF-05, RF-06)

| Método e rota | Perfil | Corpo | Resposta | Teste |
|---|---|---|---|---|
| `GET /recursos` | autenticado | — | `200 {"salas", "professores", "materiais"}`, só ativos | `InterfacePerfisApiTest` |
| `POST /salas` | ADM | `{"nome", "tipo", "capacidade", "localizacao", "restrito"}` | `201` sala; `400` campo ausente ou capacidade ≤ 0 | `InterfacePerfisApiTest` |
| `PUT /salas/{id}` | ADM | idem | `200` sala editada; `400`; `404` | `EdicaoCadastroApiTest` |
| `POST /salas/{id}/inativacao` | ADM | — | `204` | `InterfacePerfisApiTest` |
| `POST /professores` | ADM | `{"nome", "identificadorInstitucional", "competencias": [..]}` | `201`; `409` identificador repetido | `InterfacePerfisApiTest` |
| `PUT /professores/{id}` | ADM | `{"nome", "competencias": [..]}` (identificador não muda) | `200`; `400`; `404` | `EdicaoCadastroApiTest` |
| `POST /professores/{id}/inativacao` | ADM | — | `204`; `404` | `InterfacePerfisApiTest` |
| `POST /materiais` | ADM | `{"nome", "tipo", "quantidadeTotal", "restrito"}` | `201`; `400` quantidade ≤ 0 | `InterfacePerfisApiTest` |
| `PUT /materiais/{id}` | ADM | idem | `200`; `400`; `404` | `EdicaoCadastroApiTest` |
| `POST /materiais/{id}/inativacao` | ADM | — | `204`; `404` | `InterfacePerfisApiTest` |
| `GET /manutencoes` | ADM | — | `200 [{"id", "tipoRecurso", "recursoId", "recursoNome", "inicio", "fim", "motivo"}]` em ordem de início | `AdministracaoApiTest` |
| `POST /manutencoes` | ADM | `{"tipoRecurso": "SALA"\|"PROFESSOR"\|"MATERIAL", "recursoId", "inicio", "fim", "motivo", "confirmarColisao"}` | `201`; sem confirmação, `409` se colide com reserva `APROVADA`/`EM_USO` (RF-23). Com `"confirmarColisao": true`, as reservas `APROVADA` do período são canceladas (histórico, evento `CANCELAMENTO_POR_MANUTENCAO`, aviso ao Solicitante) e a manutenção entra; reserva `EM_USO` no período continua `409` | `ApiSegurancaTest`, `aprovacao-bloqueio.feature`, `AdministracaoApiTest` |
| `POST /manutencoes/{id}/encerramento` | ADM | — | `200` manutenção encerrada; o bloqueio deixa de valer na hora e o recurso volta à busca; evento `ENCERRAMENTO`; `404` inexistente ou já encerrada | `AdministracaoApiTest` |

Toda escrita de cadastro e de manutenção gera um evento de auditoria (`CRIACAO`, `ATUALIZACAO`, `INATIVACAO`,
`ENCERRAMENTO`) com o Administrador do token como `autorId` (RF-28).

## Busca de disponibilidade (RF-08 a RF-11)

| Método e rota | Perfil | Parâmetros | Resposta | Teste |
|---|---|---|---|---|
| `GET /salas/disponiveis` | autenticado | `inicio`, `fim` (obrigatórios); `tipo`, `capacidadeMinima` (≥ 0), `localizacao` | `200 [sala]`, sem reserva ativa sobreposta nem manutenção | `MatrizAutorizacaoApiTest`, `aprovacao-bloqueio.feature` |
| `GET /professores/disponiveis` | autenticado | `competencia`, `inicio`, `fim` | `200 [professor]` | `InterfacePerfisApiTest`, `EdicaoCadastroApiTest` |
| `GET /materiais/disponiveis` | autenticado | `inicio`, `fim`; `tipo`, `quantidade` (padrão 1) | `200 [material]` sem reserva ativa sobreposta nem manutenção e com `quantidadeTotal` ≥ `quantidade` (material exclusivo no intervalo, DEF-016) | `InterfacePerfisApiTest`, `EdicaoCadastroApiTest` |

## Reservas (RF-12 a RF-20, RF-27 a RF-30)

Estados: `SOLICITADA → APROVADA → EM_USO → CONCLUIDA`, e os alternativos `REJEITADA`, `CANCELADA` e `NAO_COMPARECEU`.
Toda transição grava histórico (mesma transação no PostgreSQL), um evento de auditoria e uma notificação ao
Solicitante (simulada por padrão; ver "Notificação").

| Método e rota | Perfil | Corpo | Transição / resposta | Teste |
|---|---|---|---|---|
| `POST /reservas` | SOL | `{"inicio", "fim", "finalidade", "recursos": [{"tipo", "id", "quantidade"}]}` (1 a 50 recursos) | `201`; nasce `SOLICITADA` se algum recurso é restrito (e o RES competente é notificado), senão `APROVADA`; `409` sobreposição (sala, professor ou material) ou manutenção | `MatrizAutorizacaoApiTest`, `aprovacao-bloqueio.feature`, `ConcorrenciaReservaIT` |
| `GET /reservas` | autenticado | — | `200 [reserva]`: SOL vê as próprias; RES as do seu local; ADM todas | `InterfacePerfisApiTest` |
| `GET /reservas/{id}` | autenticado | — | `200`; SOL só a própria (`403` para alheia ou inexistente) | `MatrizAutorizacaoApiTest` |
| `PUT /reservas/{id}` | SOL dono | `{"inicio", "fim"}` | `200`; `409` sobreposição ou estado já iniciado | `MatrizAutorizacaoApiTest` |
| `POST /reservas/{id}/aprovacao` | RES competente, ADM | — | `SOLICITADA → APROVADA` | `aprovacao-bloqueio.feature`, `MatrizAutorizacaoApiTest` |
| `POST /reservas/{id}/rejeicao` | RES competente, ADM | `{"justificativa"}` (obrigatória) | `SOLICITADA → REJEITADA`; justificativa visível ao Solicitante | `aprovacao-bloqueio.feature` |
| `POST /reservas/{id}/inicio-uso` | RES competente, ADM | — | `APROVADA → EM_USO` (RF-18: restrita nunca pula a aprovação) | `InterfacePerfisApiTest`, `ConclusaoNaoComparecimentoApiTest` |
| `POST /reservas/{id}/conclusao` | RES competente, ADM | — | `EM_USO → CONCLUIDA` | `ConclusaoNaoComparecimentoApiTest` |
| `POST /reservas/{id}/nao-comparecimento` | RES competente, ADM | — | `APROVADA → NAO_COMPARECEU`, só a partir de início + 15 min (`409` antes). Além do registro manual, o sistema encerra sozinho como `NAO_COMPARECEU` a reserva que chega ao **fim do horário** ainda `APROVADA` (tarefa a cada 60 s, autor `sistema`) | `ConclusaoNaoComparecimentoApiTest`, `NaoComparecimentoAutomaticoTest` |
| `POST /reservas/{id}/cancelamento` | SOL dono | `{"motivo"}` (obrigatório) | `SOLICITADA`/`APROVADA`/`EM_USO → CANCELADA` (RF-14: qualquer estado anterior a `CONCLUIDA`) | `aprovacao-bloqueio.feature`, `MatrizAutorizacaoApiTest` |
| `DELETE /reservas/{id}` | qualquer | — | sempre `403`: nenhuma reserva é apagada (RF-30); para encerrar, cancele | `aprovacao-bloqueio.feature`, `MatrizAutorizacaoApiTest` |
| `GET /reservas/{id}/historico` | RES, ADM | — | `200 [{"estadoAnterior", "estadoNovo", "usuarioId", "instante", "motivo"}]`; sem rota de escrita | `HistoricoReservaApiTest` |

## Retirada e devolução de material (RF-24 a RF-26)

| Método e rota | Perfil | Corpo | Resposta | Teste |
|---|---|---|---|---|
| `GET /reservas/{id}/retiradas` | RES competente, ADM | — | `200 [retirada]` | `InterfacePerfisApiTest` |
| `POST /reservas/{id}/retiradas` | RES competente, ADM | `{"materialId"}` | `201`; só com a reserva `EM_USO` e material da reserva; `409` ciclo aberto do mesmo material | `InterfacePerfisApiTest` |
| `POST /retiradas/{id}/devolucao` | RES competente, ADM | `{"condicao": "INTEGRO"\|"DANIFICADO"}` | `200`; `409` ciclo já encerrado | `InterfacePerfisApiTest` |
| (auditoria) | — | — | retirada grava `RETIRADA` e devolução grava `DEVOLUCAO_INTEGRO` ou `DEVOLUCAO_DANIFICADO`, entidade `Retirada`, com o autor do token | `AdministracaoApiTest` |
| `GET /retiradas/pendentes` | RES competente, ADM | — | `200 [retirada]` com `"pendente": true`: material retirado e não devolvido depois do fim da reserva (RF-26). A sinalização roda na mesma tarefa periódica do não comparecimento | `AdministracaoApiTest` |

## Relatório (RF-34 a RF-36)

| Método e rota | Perfil | Parâmetros | Resposta | Teste |
|---|---|---|---|---|
| `GET /relatorios/utilizacao` | RES, ADM | `inicio`, `fim` (`yyyy-MM-dd'T'HH:mm`, no máximo 366 dias) | `200 {"recursos": [horas por recurso], "professores": [carga horária], "conflitosEvitados"}` | `RelatorioUtilizacaoApiTest` |

Detalhes de cálculo (recorte na borda do período, canceladas não contam): seção "API — Relatório de utilização" do
[README](../README.md).

## Usuários e auditoria (Administrador; RF-28, RF-29)

| Método e rota | Perfil | Corpo / parâmetros | Resposta | Teste |
|---|---|---|---|---|
| `GET /usuarios` | ADM | — | `200 [{"id", "email", "perfil", "ativo", "localizacao"}]`; nunca traz senha nem hash | `AdministracaoApiTest` |
| `POST /usuarios` | ADM | `{"email", "senha", "perfil", "localizacao"}` (local obrigatório para RES) | `201`; e-mail em minúsculas; senha de 8 a 120 caracteres guardada com PBKDF2; `409` e-mail repetido ou local que já tem Responsável | `AdministracaoApiTest` |
| `POST /usuarios/{id}/inativacao` | ADM | — | `200`; a credencial é apagada, os tokens da conta são revogados e o Responsável deixa de responder pelo local; `409` para a própria conta; `404` conta ativa inexistente | `AdministracaoApiTest` |
| `GET /auditoria` | ADM | `entidade`, `acao` (opcionais, sem diferença de maiúsculas), `limite` (1 a 500, padrão 100) | `200 [{"entidade", "entidadeId", "acao", "autorId", "dataHora"}]`, do mais recente ao mais antigo; sem rota de escrita (`405`) | `AdministracaoApiTest` |

Eventos de usuário (`CRIACAO`, `INATIVACAO`) gravam o Administrador como autor. Eventos automáticos gravam
`00000000-0000-0000-0000-00000000005e` (sistema).

## Notificação (RF-31 a RF-33)

Não há rota pública. Cada transição de estado notifica o Solicitante, e a criação de reserva restrita notifica o
Responsável competente. Por padrão a notificação é **simulada** (registrada em memória). Com
`edualoca.notificacao.externa.habilitada=true` e `edualoca.notificacao.externa.url`, a aplicação faz
`POST {url}/notificacoes` com `Authorization: Bearer ${EDUALOCA_NOTIFICACAO_API_KEY}`. Timeout de conexão de 1 s e de
leitura de 2 s; uma nova tentativa em `5xx`. A falha do provedor nunca desfaz a transição
(`NotificacaoExternaIntegradaApiTest`, `IntegracaoNotificacaoExternaTest`).

## Exemplo de fluxo com `curl`

```bash
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"usuario@example.com","senha":"senhaValida123"}' | jq -r .token)
curl -s "localhost:8080/salas/disponiveis?inicio=2027-03-10T08:00&fim=2027-03-10T10:00&localizacao=Bloco%20A" \
  -H "Authorization: Bearer $TOKEN"
```
