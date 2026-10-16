# Jornadas de Usuário — EduAloca

> Jornadas derivadas dos "Cenários típicos de uso" de cada persona em
> [PERSONAS/](PERSONAS/) e dos requisitos em [REQUISITOS.md](REQUISITOS.md). Cada etapa
> referencia o(s) RF envolvido(s) para rastreabilidade.

## J1 — Solicitante reserva uma sala sem restrição (caminho feliz)

**Persona:** [Solicitante](PERSONAS/solicitante.md)
**Cenário de origem:** "Reservar uma sala de informática para uma aula na próxima semana."

1. Solicitante faz login. (RF-01)
2. Busca salas por tipo "laboratório de informática", capacidade mínima e horário
   desejado. (RF-08, RF-11)
3. Sistema retorna apenas salas disponíveis no intervalo, sem conflito nem manutenção.
   (RF-11)
4. Solicitante seleciona a sala e cria a reserva informando início, fim e finalidade.
   (RF-12)
5. Como a sala não é restrita, a reserva nasce no estado `APROVADA`. (RF-12)
6. Sistema registra auditoria da criação. (RF-27)
7. Sistema notifica o Solicitante da confirmação. (RF-31)

**Resultado esperado:** reserva confirmada sem intervenção de terceiros, em poucos
segundos.

---

## J2 — Solicitante tenta reservar recurso restrito (fluxo de aprovação)

**Persona:** [Solicitante](PERSONAS/solicitante.md) + [Responsável](PERSONAS/responsavel.md)
**Cenário de origem:** "Reservar um projetor (material) para uma apresentação, verificando
se ele exige aprovação por ser um recurso restrito."

1. Solicitante busca material restrito disponível no horário. (RF-10, RF-11)
2. Cria a reserva; sistema identifica recurso restrito e mantém estado `SOLICITADA`.
   (RF-12, RF-18)
3. Sistema notifica o Responsável do recurso sobre a nova solicitação pendente. (RF-32)
4. Responsável consulta a fila de pendências, avalia disponibilidade e adequação, e
   aprova. (RF-19)
5. Reserva transiciona para `APROVADA`; auditoria registrada. (RF-27)
6. Sistema notifica o Solicitante da aprovação. (RF-31)

**Fluxo alternativo (rejeição):** Responsável rejeita informando justificativa
obrigatória (RF-20); reserva vai para `REJEITADA`; Solicitante é notificado com o motivo
(RF-31, RF-38).

---

## J3 — Conflito de horário detectado (caminho de erro)

**Persona:** [Solicitante](PERSONAS/solicitante.md)
**Cenário de origem:** "Tentar reservar um horário que já está ocupado e receber uma
mensagem explicando o conflito."

1. Solicitante tenta criar reserva para sala/professor/material já ocupado no intervalo.
   (RF-12)
2. Sistema detecta sobreposição com reserva ativa existente. (RF-15, RF-16)
3. Sistema responde HTTP 409 com mensagem compreensível identificando o recurso e o
   horário conflitante — não um erro técnico genérico. (RF-38)
4. Nenhuma reserva é criada; nenhuma auditoria de "criação" é gerada (apenas, no máximo, um
   registro de tentativa rejeitada para fins de relatório de conflitos evitados). (RF-35)

---

## J4 — Duas solicitações simultâneas disputam o mesmo recurso

**Persona:** [Solicitante](PERSONAS/solicitante.md) (dois usuários distintos)
**Cenário de origem:** Regra crítica de concorrência + dor "Perder a reserva para outro
usuário por concorrência simultânea sem entender o motivo."

1. Solicitante A e Solicitante B enviam, quase simultaneamente, requisições de reserva
   para o mesmo recurso e mesmo intervalo. (RF-17)
2. Sistema serializa a decisão (ex.: lock otimista/pessimista ou constraint de banco) e
   aceita exatamente uma requisição. (RF-17)
3. O solicitante vencedor recebe confirmação (`APROVADA` ou `SOLICITADA`, conforme
   restrição). (RF-12)
4. O solicitante perdedor recebe HTTP 409 com mensagem explicando que o recurso acabou de
   ser reservado por outra solicitação. (RF-38)

---

## J5 — Administrador cadastra manutenção em recurso já parcialmente reservado

**Persona:** [Administrador](PERSONAS/administrador.md)
**Cenário de origem:** "Colocar um laboratório em manutenção por uma semana, bloqueando
automaticamente novas reservas nesse período."

1. Administrador acessa o cadastro do laboratório e registra período de manutenção
   (início, fim, motivo). (RF-21)
2. Sistema verifica se há reservas `APROVADA`/`EM_USO` no período informado. (RF-23)
3. Caso existam, sistema exige confirmação explícita do Administrador (ou recusa o
   cadastro, conforme política adotada na implementação). (RF-23)
4. Após confirmado, o recurso passa a ser excluído de toda busca de disponibilidade no
   período de manutenção. (RF-11, RF-22)
5. Auditoria da manutenção é registrada. (RF-28)

---

## J6 — Responsável controla retirada e devolução de material

**Persona:** [Responsável](PERSONAS/responsavel.md)
**Cenário de origem:** "Registrar a retirada de um equipamento por um professor e,
posteriormente, confirmar sua devolução."

1. Reserva de material transiciona para `EM_USO` (ex.: no início do horário agendado).
2. Responsável registra a retirada do item pelo Solicitante: data/hora e identificação.
   (RF-24)
3. Ao final do uso, Responsável registra a devolução, incluindo condição do item.
   (RF-25)
4. Caso o prazo de devolução vença sem registro, sistema sinaliza pendência ao
   Responsável. (RF-26)
5. Toda ação gera auditoria. (RF-27)

---

## J7 — Administrador investiga histórico de uma reserva cancelada

**Persona:** [Administrador](PERSONAS/administrador.md)
**Cenário de origem:** "Investigar, via histórico auditável, por que uma reserva foi
cancelada ou alterada."

1. Administrador localiza a reserva em questão.
2. Consulta a trilha de auditoria associada: estados anteriores, autor de cada mudança,
   data/hora e motivo. (RF-27, RF-29)
3. Sistema apresenta o histórico completo e imutável, sem opção de edição/exclusão.
   (RF-29, RF-30)

---

## J8 — Administrador gera relatório mensal de utilização

**Persona:** [Administrador](PERSONAS/administrador.md)
**Cenário de origem:** "Gerar um relatório mensal de utilização de salas para apresentar à
direção da escola."

1. Administrador seleciona intervalo de datas (ex.: mês corrente) e tipo de recurso.
2. Sistema calcula e exibe: quantidade de reservas, carga horária alocada e conflitos
   evitados por recurso no período. (RF-34, RF-35)
3. Solicitante não tem acesso a este relatório (RF-36), reforçando a separação de
   permissões por perfil.

---

## J9 — Ciclo de vida completo de uma reserva (estados)

Jornada de referência para implementação da máquina de estados (Regras críticas /
"Estados e auditoria" do enunciado):

```
SOLICITADA --(aprovação, RF-19)--> APROVADA --(início do uso)--> EM_USO --(fim do uso)--> CONCLUÍDA
    |                                  |
    |--(rejeição, RF-20)--> REJEITADA  |--(cancelamento, RF-14)--> CANCELADA
    |--(cancelamento, RF-14)--> CANCELADA
                                       |--(solicitante não aparece)--> NAO_COMPARECEU
```

Toda seta representa uma transição que deve gerar auditoria (RF-27) e, quando aplicável,
notificação ao Solicitante (RF-31).
