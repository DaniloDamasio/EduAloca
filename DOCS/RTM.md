# RTM — Matriz de Rastreabilidade de Requisitos

> **Status: cobertura inicial.** Este arquivo começa com os requisitos efetivamente
> cobertos pela entrega do adapter HTTP de notificação externa (WireMock). Ele **não**
> representa ainda 100% dos requisitos críticos listados em
> [REQUISITOS.md](REQUISITOS/REQUISITOS.md) — RNF-11 exige essa cobertura completa, o que
> é um trabalho incremental, não esgotado nesta entrega. Novas linhas devem ser
> adicionadas à medida que cada requisito crítico ganha teste e evidência formal.

| Requisito | Risco | Teste | Evidência / Execução |
|---|---|---|---|
| RF-31 — Notificar o Solicitante a cada mudança de estado da própria reserva (REQUISITOS.md:99) | Notificação não disparada ou disparada para destinatário errado após transição de estado. | `NotificacoesTest.rf31T1_mudancaDeEstadoDaReserva_deveNotificarSolicitante` (lógica de domínio); `IntegracaoNotificacaoExternaTest.int01_respostaValida_deveRetornarSucessoEEmitirRequestEsperado` (transporte HTTP real via WireMock). | `./mvnw -B verify` — suíte verde, executada no CI (`.github/workflows/ci.yml`) a cada PR. |
| RF-32 — Notificar o Responsável competente ao surgir solicitação restrita pendente (REQUISITOS.md:100) | Responsável não identificado ou exceção não tratada quando recurso restrito não tem responsável associado. | `NotificacoesTest.rf32T1_novaSolicitacaoRestrita_deveNotificarResponsavelCompetente`; `NotificacoesTest.rf32T2_recursoRestritoSemResponsavelAssociado_deveRetornarFalha`. | Idem acima. |
| RF-33 — Falha de notificação externa não deve reverter a transição de estado (REQUISITOS.md:101) | Indisponibilidade, timeout ou erro do provedor externo propaga exceção e desfaz a transição de negócio. | `NotificacoesTest.rf33T1/T2` (lógica isolada do transporte); `IntegracaoNotificacaoExternaTest.int02_resposta404_naoDeveReenviar`, `int03_resposta500_deveReenviarUmaVezAntesDeFalhar`, `int04_timeout_deveInterromperSemLancarExcecao`, `int05_corpoInvalido_naoDeveLancarExcecao`. | Idem acima. |
| RNF-16 — Integrações externas isoladas por interface, substituíveis por WireMock em teste (REQUISITOS.md:148) | Adapter acoplado a detalhes de transporte, impossibilitando teste determinístico e offline. | `NotificacaoService.NotificadorExterno` (porta) + `NotificacaoHttpAdapter` (adaptador real) cobertos por `IntegracaoNotificacaoExternaTest` (WireMock em porta dinâmica, sem rede externa). | `./mvnw -B verify`; suíte roda offline. |
| RNF-16 (segurança da chamada externa) | Token/credencial do provedor vazado em log ou propagado ao cliente do EduAloca. | `IntegracaoNotificacaoExternaTest.seguranca_tokenVazadoNoCorpoDeErro_naoDeveAtravessarParaLogNemRetorno`. | Idem acima. |

## Pendências de cobertura (RNF-11)

Os demais RFs/RNFs críticos listados em [REQUISITOS.md](REQUISITOS/REQUISITOS.md) ainda
não têm linha nesta matriz. Cada nova entrega de feature deve adicionar aqui o
requisito, o risco que o teste mitiga, o(s) teste(s) correspondente(s) e a forma de
execução/evidência, até atingir 100% dos requisitos marcados "Crítico".
