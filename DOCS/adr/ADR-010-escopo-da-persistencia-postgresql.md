# ADR-010 — Escopo da persistência em PostgreSQL nesta entrega

- **Status:** Aceito (12/11/2026)
- **Refina:** ADR-007 (PostgreSQL como SGBD) e ADR-002 (auditoria na mesma transação), em
  [REVISAO_ATAM.md §3](../REVISAO_ATAM.md#3-adrs)
- **Motivado por:** [relatório de qualidade](../relatorio-qualidade.md), que apontou que só parte dos dados é persistida
- **Requisitos:** RF-15, RF-16, RF-17, RF-27, RF-29, RNF-08, RNF-09, RNF-15

## Contexto

O enunciado fixa PostgreSQL na stack mínima, e o ADR-007 o adotou. Na prática, a aplicação tem dois modos:

| Dado | Onde fica | Observação |
|---|---|---|
| Reserva e recursos da reserva (`reserva`, `reserva_recurso`) | **PostgreSQL** quando `edualoca.persistencia.url` está configurada; memória caso contrário | Constraint `EXCLUDE USING gist` (ADR-009) |
| Histórico de estados da reserva (`reserva_historico`) | **PostgreSQL**, na mesma transação da mudança de estado; memória caso contrário | Trigger recusa `UPDATE`/`DELETE` (RF-29) |
| Salas, professores, materiais, manutenções | Memória (`Banco`) | Perdidos ao reiniciar |
| Usuários e credenciais (hash PBKDF2) | Memória (`Banco`), contas de seed | Perdidos ao reiniciar |
| Eventos de auditoria de cadastro (`AuditoriaService`) | Memória | Não entram na transação do PostgreSQL |
| Retiradas e devoluções, notificações | Memória | Perdidas ao reiniciar |

A pergunta é se a entrega deve migrar tudo para o PostgreSQL ou registrar o escopo atual como decisão.

## Alternativas

1. **Migrar todos os dados para o PostgreSQL agora.** Repositórios JDBC para sala, professor, material, manutenção,
   usuário, auditoria e retirada, com ITs em Testcontainers. Resolve a perda de dados no reinício e coloca a auditoria
   de cadastro na mesma transação (ADR-002). Custo: tocar quase todos os serviços de domínio, que hoje leem `Banco`
   diretamente, e reabrir regras críticas já auditadas por mutação (manutenção, aprovação competente, retirada), com
   risco real de regressão perto do fim do semestre.
2. **Manter o PostgreSQL onde estão as regras que dependem de garantia de banco e registrar o restante como dívida
   conhecida.** O banco protege o que o teste de concorrência e a auditoria precisam: exclusividade de horário entre
   instâncias (DEF-001) e histórico imutável e transacional. O restante continua em memória, isolado no `Banco`.

## Decisão

Adotar a **alternativa 2** para esta entrega acadêmica.

- PostgreSQL é obrigatório para as garantias críticas: RF-17/RNF-08 (constraint de exclusão; `ConcorrenciaReservaIT`,
  `ConcorrenciaReservaMultiInstanciaIT`), RF-15/RF-16 (`ReservaRepositorioJdbcIT`) e RF-27/RF-29 (histórico na mesma
  transação e imutável; `HistoricoReservaTransacaoIT`). Essas garantias são verificadas contra PostgreSQL real.
- A rodada de carga de referência roda com PostgreSQL ([performance/rnf.md §4](../performance/rnf.md)).
- O modo padrão, sem `edualoca.persistencia.url`, continua em memória, para demonstração e testes rápidos. O
  `ReservaService` usa o mesmo lock por recurso nos dois modos (`ConcorrenciaReservaEmMemoriaTest`).

## Consequências

- **Aceito:** reiniciar a aplicação apaga cadastros, usuários criados em execução, retiradas, notificações e eventos de
  auditoria de cadastro. No modo PostgreSQL, as reservas sobrevivem, mas passam a apontar para recursos que o seed
  recria com outros ids. Por isso, **o modo PostgreSQL só é usado com banco descartável** (testes e carga).
- **Aceito:** a auditoria de cadastro não é transacional nem imutável no banco. O histórico de **reservas** é (RF-27,
  RF-29). O ADR-002 vale integralmente só para reservas.
- **Aceito:** com mais de uma instância, os dados em memória divergem entre elas. O ADR-004 (instância única) continua
  valendo, e a constraint do banco cobre só o que é crítico para dupla reserva.
- **Risco:** RNF-09 (integridade referencial entre reserva, recurso e auditoria) só é verificável dentro do banco para
  `reserva` ↔ `reserva_recurso` ↔ `reserva_historico`. `reserva_recurso.recurso_id` não tem chave estrangeira, porque
  as tabelas de recurso não existem.

## Caminho de migração (quando houver prazo)

1. Tabelas `sala`, `professor`, `material`, `manutencao` com as colunas da [modelagem](../REQUISITOS/MODELAGEM_DADOS.md)
   e chave estrangeira de `reserva_recurso.recurso_id` por tipo.
2. Interfaces de repositório por agregado no domínio (como já existe `ReservaRepositorio`), com implementação em memória
   para os testes unitários e JDBC para a aplicação. Trocar os acessos diretos a `Banco` serviço a serviço, cada troca
   protegida pelos testes e mutantes já existentes (C1–C24, M1–M6, R1–R11).
3. `usuario` com `senha_hash` (o formato PBKDF2 já é autodescritivo) e `auditoria_evento` na mesma transação da escrita
   de cadastro, fechando o ADR-002 por completo.
4. Migrations versionadas (Flyway) no lugar do `schema.sql` aplicado na subida.

## Atualização (2026-11-13): catálogo persistido e reservas recarregadas

O primeiro passo do caminho de migração foi feito, sem trocar a arquitetura de cache:

- **Catálogo no banco:** tabelas `sala`, `professor`, `professor_competencia` (com a ordem) e `material`. Cada cadastro,
  edição e inativação grava no banco pela porta `CatalogoRepositorio` (`CatalogoRepositorioJdbc`); no modo em memória a
  porta é `NENHUM`.
- **Recarga na subida:** o catálogo gravado vence o seed, e o seed ausente é inserido sem sobrescrever nada
  (`ON CONFLICT DO NOTHING`, seguro com duas instâncias subindo juntas). Em seguida as reservas são recarregadas do banco,
  com cada recurso resolvido por tipo e id no catálogo já carregado. Isso acontece na criação dos beans, antes de a
  aplicação atender requisições.
- **Seed com ids fixos** (`Banco.SALA_SEED_ID`, `PROFESSOR_SEED_ID` e `MATERIAL_SEED_ID`), para não duplicar a cada subida.
- **O `Banco` em memória continua sendo o cache de leitura:** o domínio segue comparando recursos por referência de
  objeto, e as leituras não mudaram. Por isso a concorrência e a carga medidas continuam valendo.

Testes: `CatalogoRepositorioJdbcIT` (upsert, competências, identificador repetido → 409, banco vence o seed, seed sem
duplicar) e `PersistenciaReinicioIT`. Este último sobe a aplicação inteira, cadastra e reserva, desliga, zera a memória e
sobe de novo no mesmo banco. Dois mutantes manuais (sem a recarga de reservas; sem gravar a edição de sala) fazem o
`PersistenciaReinicioIT` falhar.

**Continua em memória:** manutenções, usuários e credenciais, auditoria, retiradas, notificações, tokens e tentativas
de login. Com duas instâncias, uma instância só vê o catálogo gravado pela outra quando reinicia: o cache não é
invalidado entre instâncias. A dupla reserva entre instâncias continua garantida pela constraint do banco.

