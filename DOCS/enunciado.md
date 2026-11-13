> **⚠️ SINGLE SOURCE OF TRUTH — DOCUMENTO IMUTÁVEL**
>
> Este arquivo é a **única fonte de verdade** sobre o escopo, os requisitos e as regras
> do projeto EduAloca. Qualquer agente (humano ou IA) que trabalhe neste repositório
> deve **consultar este documento antes de tomar decisões de arquitetura, modelagem ou
> implementação**, e tratá-lo como **autoritativo em caso de conflito** com qualquer
> outra documentação, comentário de código, suposição ou conversa anterior.
>
> **Não altere o conteúdo normativo deste arquivo** (objetivo, escopo, requisitos,
> regras críticas, metas mensuráveis, entregáveis, stack) sem solicitação explícita do
> responsável pelo projeto. Dúvidas de interpretação devem ser esclarecidas com o
> responsável, nunca resolvidas por suposição silenciosa.

# Objetivo

Desenvolver e validar uma aplicação que organize a alocação de salas, professores e materiais sem conflitos de horário. O foco da avaliação não será apenas fazer o sistema funcionar, mas apresentar evidências objetivas de qualidade, segurança, rastreabilidade e desempenho.

# Escopo e perfis

- Solicitante:
Professor ou coordenador que consulta a disponibilidade e cria, altera ou cancela suas reservas.

- Responsável:
Aprova solicitações especiais, valida a alocação de docentes e acompanha retiradas e devoluções de material.

- Administrador:
Gerencia salas, professores, materiais, usuários, bloqueios e períodos de manutenção.

# Requisitos funcionais obrigatórios

1. Autenticação e autorização por perfil.
2. Cadastro e consulta de salas, professores e materiais.
3. Pesquisa por tipo, capacidade, localização, competência e disponibilidade.
4. Criação, alteração e cancelamento de reservas.
5. Detecção de sobreposição de horários, inclusive na agenda do professor.
6. Proteção contra dupla reserva em solicitações simultâneas.
7. Aprovação obrigatória para recursos restritos.
8. Bloqueio de recursos em manutenção.
9. Registro de retirada e devolução de materiais e equipamentos.
10. Histórico auditável de mudanças.
11. Notificação simulada ou integração com API externa.
12. Relatório de utilização por recurso, carga horária alocada e conflitos evitados.
13. Interface responsiva e mensagens de erro compreensíveis.
14. Documentação de API ou dos fluxos públicos do sistema.

# Regras críticas

## Tempo e concorrência
- O término deve ser posterior ao início.
- Reservas do mesmo recurso não podem se sobrepor — sala, material ou professor.
- Duas solicitações simultâneas devem produzir uma única reserva aceita.
- Recursos em manutenção não podem ser reservados.

## Estados e auditoria

SOLICITADA → APROVADA → EM_USO → CONCLUÍDA

- Estados alternativos: rejeitada, cancelada e não compareceu.
- Somente responsáveis aprovam recursos restritos.
- Reservas iniciadas não podem ser apagadas.
- Toda mudança de estado deve gerar auditoria.

# Estratégia obrigatória de qualidade

## Testes automatizados
Unitários com JUnit 5, parametrizados, integração, API em caixa-preta e end-to-end.

## Ambiente realista
Banco containerizado com Testcontainers e WireMock para integrações externas.

## Concorrência
Teste automatizado comprovando a proteção contra dupla reserva.

## TDD
Ao menos uma funcionalidade nova desenvolvida com evidências da técnica.

## CI e análise estática
GitHub Actions em pull requests, cobertura por JaCoCo e projeto integrado ao SonarCloud.

## Não funcionais
JMeter, autorização, validação de entrada e tratamento seguro de erros.

Uso de mocks: permitido em testes unitários isolados. Persistência e integrações críticas devem ser verificadas com ambiente real, Testcontainers ou WireMock.

# Metas mensuráveis

- Pelo menos 80% de cobertura de linhas
- Pelo menos 70% de cobertura de branches
- 100% dos requisitos críticos na RTM
- 0 bugs ou vulnerabilidades críticas

Cobertura alta, sozinha, não garante qualidade: testes triviais feitos apenas para elevar o percentual não asseguram pontuação máxima.

# Entregáveis

- Repositório com histórico de trabalho e revisão por pares.
- Aplicação executável e instruções de instalação.
- README.md e documentação dos fluxos ou da API.
- RTM.md ligando requisito, risco, teste e evidência.
- Diagramas de contexto, componentes e sequências críticas.
- Plano, suíte automatizada e relatório de testes.
- Pipeline de integração contínua.
- Relatórios de SonarCloud e cobertura (JaCoCo).
- Plano e resultado do teste de carga.
- Registro de defeitos encontrados, priorizados e corrigidos.
- Relatório final de qualidade.
- Apresentação e demonstração do sistema.

# Stack mínima necessária
- Java 21 SpringBoot 4.1.1 Maven
- Arquitetura MVC
- PostgreSQL (ver justificativa em [DOCS/arquitetura.md](arquitetura.md#11-decisão-de-banco-de-dados-postgresql))


