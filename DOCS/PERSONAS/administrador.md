# Persona: Administrador

## Quem é

Responsável pela gestão operacional e estrutural do sistema EduAloca dentro da instituição
de ensino. Normalmente alguém da equipe de TI, secretaria acadêmica ou coordenação geral,
com visão completa sobre todos os recursos, usuários e regras de funcionamento do sistema.

## Objetivos

- Manter o cadastro de salas, professores, materiais e usuários sempre atualizado e
  correto.
- Garantir que o sistema reflita a realidade física da instituição (recursos realmente
  disponíveis, em manutenção, desativados, etc.).
- Configurar bloqueios e períodos de manutenção para evitar reservas em recursos
  indisponíveis.
- Ter uma visão consolidada do uso dos recursos (relatórios de utilização, carga horária
  alocada, conflitos evitados) para apoiar decisões institucionais.
- Garantir a integridade, segurança e auditabilidade de todo o sistema.

## Dores e frustrações

- Dados desatualizados ou inconsistentes (ex.: sala cadastrada que não existe mais,
  capacidade errada) gerando reservas inválidas na prática.
- Falta de visibilidade sobre o uso real dos recursos ao longo do tempo, dificultando
  decisões (ex.: investir em mais salas ou materiais).
- Esquecer de cadastrar manutenções, permitindo reservas em recursos indisponíveis.
- Processos manuais de gestão de usuários e permissões, sujeitos a erro humano.
- Ausência de histórico/auditoria que dificulte investigar problemas (quem alterou o quê e
  quando).
- Sobrecarga de tarefas administrativas que poderiam ser automatizadas ou simplificadas
  pela interface.

## Necessidades do sistema (o que o motiva a usar bem a ferramenta)

- CRUD completo e confiável de salas, professores, materiais e usuários.
- Ferramenta simples para registrar bloqueios e períodos de manutenção, com efeito
  imediato sobre a disponibilidade dos recursos.
- Controle de perfis de acesso (autenticação e autorização) para Solicitantes,
  Responsáveis e outros Administradores.
- Relatórios de utilização por recurso, carga horária alocada e conflitos evitados,
  exportáveis ou visualizáveis facilmente.
- Histórico auditável completo de todas as mudanças de estado e cadastro no sistema.
- Confiança de que o sistema é seguro (tratamento adequado de erros, validação de
  entradas) e performático mesmo sob uso concorrente.

## Nível de permissão (conforme enunciado)

Gerencia salas, professores, materiais, usuários, bloqueios e períodos de manutenção. É o
perfil com acesso mais amplo do sistema, incluindo configurações que afetam todos os
demais perfis.

## Cenários típicos de uso

- Cadastrar uma nova sala, definindo tipo, capacidade, localização e se é um recurso
  restrito (exige aprovação).
- Colocar um laboratório em manutenção por uma semana, bloqueando automaticamente novas
  reservas nesse período.
- Criar um novo usuário com perfil de Responsável e definir sobre quais recursos ele tem
  autoridade.
- Gerar um relatório mensal de utilização de salas para apresentar à direção da escola.
- Investigar, via histórico auditável, por que uma reserva foi cancelada ou alterada.
- Desativar o cadastro de um professor que não faz mais parte da instituição.

## Relação com os requisitos funcionais

Relaciona-se com praticamente todos os requisitos funcionais, com destaque para os
requisitos 1, 2, 8, 10, 12 e 14 do enunciado (autenticação/autorização, cadastro de
salas/professores/materiais, bloqueio de recursos em manutenção, histórico auditável,
relatórios de utilização e documentação de API/fluxos).
