# Apresentação e demonstração do EduAloca

Roteiro para a apresentação final (cerca de 15 minutos: 8 de slides e 7 de demonstração). Cada slide aponta para a
evidência no repositório, para que qualquer número citado possa ser conferido na hora. Trabalho individual de Danilo
Damasio.

## Preparação (antes de entrar na sala)

1. Conferir o último run de CI verde na `main` e abrir a aba do SonarCloud do projeto.
2. Subir a aplicação no modo em memória: `./mvnw -B package -DskipTests` e `java -jar target/edualoca-0.0.1-SNAPSHOT.jar`.
3. Abrir <http://localhost:8080/> numa janela e o `target/site/jacoco/index.html` noutra.
4. Ter à mão [RTM.md](RTM.md), [relatorio-qualidade.md](relatorio-qualidade.md) e o resultado de carga mais recente em
   [performance/](performance/).
5. Se a demonstração ao vivo falhar, usar o E2E como plano B: `./mvnw -B verify -Dit.test=FluxoCompletoNavegadorIT`
   percorre o mesmo fluxo no navegador.

## Slides

| # | Título | Conteúdo | Evidência |
|---|---|---|---|
| 1 | O problema | Salas, professores e materiais reservados sem conflito; 3 perfis | [enunciado.md](enunciado.md), [PERSONAS/](PERSONAS/) |
| 2 | Arquitetura | Contexto, componentes e sequência da reserva concorrente (C4); camadas verificadas por teste | [arquitetura.md](arquitetura.md) §14–17; `ArquiteturaCamadasTest` |
| 3 | Regras críticas | Fim > início; sem sobreposição de sala, material ou professor; uma reserva em pedidos simultâneos; manutenção bloqueia; máquina de estados com auditoria | [RTM.md](RTM.md) |
| 4 | Dupla reserva | Constraint de exclusão GiST no PostgreSQL (ADR-009); 20 pedidos simultâneos em 1 e em 2 JVMs → 1 aceito | `ConcorrenciaReservaIT`, `ConcorrenciaReservaMultiInstanciaIT`; [DEF-001](defeitos/DEF-001-dupla-reserva-entre-instancias.md) |
| 5 | Estratégia de teste | Unitários e parametrizados (JUnit 5), API caixa-preta, BDD (Cucumber), integração (Testcontainers), WireMock, E2E (Playwright), TDD com commits red/green | [testes/plano-tdd.md](testes/plano-tdd.md), [testes/evidencia-tdd.md](testes/evidencia-tdd.md) |
| 6 | Metas | Linhas ≥ 80%, ramos ≥ 70%, 100% dos críticos na RTM, 0 bugs/vulnerabilidades críticas: mostrar o número medido de cada uma | [relatorio-qualidade.md](relatorio-qualidade.md) §4, §8, §9; JaCoCo; SonarCloud |
| 7 | Cobertura não é qualidade | Mutação manual: testes que passavam com qualquer código (DEF-011) e regras sem teste (DEF-014, DEF-015) | [testes/mutacao-manual.md](testes/mutacao-manual.md) |
| 8 | Defeitos | Registrados, priorizados e corrigidos, cada um com teste que falhava antes da correção | [defeitos/](defeitos/) |
| 9 | Desempenho | Plano (50 usuários, disputa por janela) e resultado: p95, vazão, 0 duplas reservas | [performance/rnf.md](performance/rnf.md) e o resultado da rodada mais recente |
| 10 | Processo | CI obrigatório na `main`, gate de cobertura, SonarCloud, E2E; revisão por pares substituída por verificações automáticas (trabalho individual) | README, "Autoria e revisão"; `.github/workflows/ci.yml` |
| 11 | Limites conhecidos | Com PostgreSQL, catálogo e reservas sobrevivem ao reinício; manutenções, usuários e auditoria ainda em memória (ADR-010), tokens sem expiração e sem limite de tentativas no login, material exclusivo no horário (DEF-016, opção A) e alteração de reserva só de horário | [relatorio-qualidade.md](relatorio-qualidade.md) §9.3 |

## Demonstração (7 minutos)

Use os botões de conta de demonstração da tela de login. Cada passo mostra uma regra do enunciado.

| Passo | Perfil | Ação | O que mostra |
|---|---|---|---|
| 1 | Administrador | *Cadastro e manutenção*: cadastrar "Lab de Robótica" (Bloco A, **restrita**) e "Kit Arduino" (5) | RF-04/06; o seed não tem recurso restrito |
| 2 | Solicitante | *Buscar e reservar*: buscar sala no Bloco A, adicionar a sala e 2 kits, solicitar | RF-08/10/12: nasce **Solicitada** |
| 3 | Solicitante | Repetir a mesma reserva; depois fim antes do início | RF-15: 409 compreensível; 400 com mensagem em português |
| 4 | Responsável (Bloco A) | *Aprovações e uso*: aprovar, iniciar uso, registrar retirada e devolução, concluir, abrir o histórico | RF-18/19/24/25/27: transições e histórico auditável |
| 5 | Responsável (Bloco A) | *Relatório* do mês | RF-34/35: horas por recurso, carga por professor, conflitos evitados |
| 6 | Administrador | *Bloquear para manutenção* sobre uma reserva aprovada: a tela pede confirmação | RF-21/23 |
| 7 | Administrador | *Usuários*: criar Responsável do "Bloco C"; *Auditoria*: filtrar por Usuário | Gestão de usuários; RF-28 |
| 8 | (qualquer) | Reduzir a janela para 360 px | RF-37: sem rolagem horizontal |

## Perguntas prováveis

- **"Como você sabe que a cobertura não é inflada?"** Mutação manual (slide 7) e testes que só passam com a regra certa
  (ex.: `NaoComparecimentoAutomaticoTest` cobre a borda -1/0/+1 minuto).
- **"E se a aplicação rodar em duas instâncias?"** A constraint do banco garante uma reserva por recurso e intervalo,
  provado com duas JVMs (slide 4). No modo em memória só o lock da JVM protege; por isso o modo de produção previsto é
  o PostgreSQL (ADR-010).
- **"Onde está a revisão por pares?"** Trabalho individual: substituída por CI obrigatório, gates automáticos e mutação
  manual. O limite está declarado no README.
- **"Por que não Spring Security?"** Token opaco resolvido no servidor e perfil checado no domínio, suficiente para o
  escopo; expiração e limite de tentativas estão registrados como pendência de segurança.
