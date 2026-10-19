# Objetivos do Projeto — EduAloca

> Este documento deriva do objetivo declarado em [enunciado.md](enunciado.md) (fonte de
> verdade). Em caso de conflito, o enunciado prevalece.

## Objetivo geral

Desenvolver e validar uma aplicação web que organize a alocação de salas, professores e
materiais de uma instituição de ensino, eliminando conflitos de horário e dupla reserva de
recursos, com evidências objetivas de qualidade, segurança, rastreabilidade e desempenho.

## Objetivos específicos

1. **Eliminar conflitos de agenda.** Garantir que nenhum recurso (sala, material ou
   professor) seja reservado em horários sobrepostos, inclusive sob concorrência
   simultânea.
2. **Formalizar o ciclo de vida da reserva.** Implementar a máquina de estados
   `SOLICITADA → APROVADA → EM_USO → CONCLUÍDA` (com os desvios `REJEITADA`,
   `CANCELADA`, `NAO_COMPARECEU`), garantindo transições válidas e auditadas.
3. **Diferenciar autoridade por perfil.** Assegurar que Solicitante, Responsável e
   Administrador tenham exatamente as permissões descritas no enunciado, nem mais, nem
   menos.
4. **Garantir rastreabilidade total.** Toda mudança de estado ou cadastro relevante deve
   gerar um registro de auditoria imutável, permitindo reconstruir "quem fez o quê e
   quando".
5. **Apoiar decisões institucionais com dados.** Fornecer relatórios de utilização por
   recurso, carga horária alocada e conflitos evitados.
6. **Demonstrar qualidade com evidências objetivas**, e não apenas funcionamento:
   - ≥ 80% de cobertura de linhas e ≥ 70% de cobertura de branches (JaCoCo);
   - 100% dos requisitos críticos rastreados na RTM;
   - 0 bugs ou vulnerabilidades críticas (SonarCloud);
   - teste automatizado comprovando proteção contra dupla reserva concorrente;
   - ao menos uma funcionalidade desenvolvida via TDD, com evidências do ciclo
     red-green-refactor;
   - pipeline de CI executando testes e análise estática a cada pull request;
   - plano e resultado de teste de carga (JMeter).
7. **Entregar uma interface utilizável** por professores, coordenadores e administradores,
   responsiva e com mensagens de erro compreensíveis (não técnicas).

## Fora de escopo (nesta fase)

- Integração real com sistemas acadêmicos externos (ex.: SIGAA, Moodle) — a notificação
  externa pode ser simulada ou via WireMock.
- Cobrança/financeiro associado ao uso de recursos.
- Aplicativo mobile nativo (a responsividade web cobre o uso em celular).

## Critérios de sucesso do projeto

| Critério | Meta |
|---|---|
| Cobertura de linhas | ≥ 80% |
| Cobertura de branches | ≥ 70% |
| Requisitos críticos na RTM | 100% |
| Bugs/vulnerabilidades críticas (SonarCloud) | 0 |
| Teste de concorrência de dupla reserva | Presente e passando no CI |
| Evidência de TDD | Pelo menos 1 funcionalidade documentada |
| Pipeline de CI em PRs | Ativo e obrigatório para merge |
| Teste de carga (JMeter) | Executado, com plano e relatório |
