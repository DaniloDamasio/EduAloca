# BDD — Aprovação, autoaprovação, bloqueio por manutenção e máquina de estados da Reserva

> **Papel:** análise de qualidade (somente especificação; nenhum código foi alterado nesta etapa).
>
> **Fontes consultadas:** [enunciado.md](../enunciado.md) (fonte de verdade),
> [REQUISITOS.md](../REQUISITOS/REQUISITOS.md) (o "PRD" do projeto — não existe `prd.md`),
> [RTM.md](../RTM.md), [JORNADAS.md](../REQUISITOS/JORNADAS.md) (J2, J5, J9),
> [MODELAGEM_DADOS.md](../REQUISITOS/MODELAGEM_DADOS.md), [plano-tdd.md](plano-tdd.md) e o código em
> `src/main/java/com/edualoca/domain/reserva/` e `domain/recurso/CadastroRecursoService.java`.
>
> **Convenção:** quando o PRD não decide uma regra, o cenário traz **❓ Pergunta em aberto (P-xx)**
> no lugar do resultado esperado. Nenhuma política foi inventada. O comportamento atual do código
> aparece só como *observação* e **não** serve de critério de aceite.
>
> **Status da RTM:** os cenários de API deste documento estão ligados a requisito, teste ou feature e
> commit na seção "Aprovação, bloqueio por manutenção e máquina de estados da Reserva" da [RTM.md](../RTM.md).
> Eles rodam como especificação executável em
> [`src/test/resources/features/aprovacao-bloqueio.feature`](../../src/test/resources/features/aprovacao-bloqueio.feature).

---

## Regra 1 — Recurso restrito exige aprovação de um Responsável

**Origem:** enunciado "Aprovação obrigatória para recursos restritos" e "Somente responsáveis
aprovam recursos restritos"; RF-12, RF-18, RF-19, RF-20; J2.

```gherkin
Funcionalidade: Aprovação obrigatória para recursos restritos

  Contexto:
    Dado a sala "Lab Química" marcada como restrita
    E a sala "Sala 101" marcada como não restrita
    E o usuário "ana" com perfil SOLICITANTE
    E o usuário "rui" com perfil RESPONSAVEL
    E o usuário "adm" com perfil ADMINISTRADOR

  # BDD-R1-01 (RF-12)
  Cenário: Reserva com recurso restrito nasce SOLICITADA
    Quando "ana" cria uma reserva da "Lab Química" de 10/03 10:00 a 12:00 com finalidade "Aula prática"
    Então a reserva é criada no estado SOLICITADA
    E um evento de auditoria "CRIACAO" é registrado com autor "ana"

  # BDD-R1-02 (RF-12)
  Cenário: Reserva sem recurso restrito nasce APROVADA, sem passar por aprovação
    Quando "ana" cria uma reserva da "Sala 101" de 10/03 10:00 a 12:00
    Então a reserva é criada no estado APROVADA

  # BDD-R1-03 (RF-12, MODELAGEM_DADOS "exigeAprovacao = algum recurso restrito")
  Cenário: Basta um recurso restrito para a reserva inteira exigir aprovação
    Quando "ana" cria uma reserva com "Sala 101" e o material restrito "Microscópio"
    Então a reserva é criada no estado SOLICITADA

  # BDD-R1-04 (RF-19)
  Cenário: Responsável aprova reserva restrita
    Dado uma reserva de "ana" na "Lab Química" no estado SOLICITADA
    Quando "rui" aprova a reserva
    Então a reserva passa ao estado APROVADA
    E um evento de auditoria "APROVACAO" é registrado com autor "rui"

  # BDD-R1-05 (RF-19 — o PRD inclui o Administrador)
  Cenário: Administrador aprova reserva restrita
    Dado uma reserva de "ana" na "Lab Química" no estado SOLICITADA
    Quando "adm" aprova a reserva
    Então a reserva passa ao estado APROVADA

  # BDD-R1-06 (RF-20)
  Cenário: Responsável rejeita com justificativa
    Dado uma reserva de "ana" na "Lab Química" no estado SOLICITADA
    Quando "rui" rejeita a reserva com justificativa "Laboratório reservado para banca"
    Então a reserva passa ao estado REJEITADA
    E a justificativa fica visível para "ana"

  # BDD-R1-07 (RF-20)
  Esquema do Cenário: Rejeição sem justificativa é recusada
    Dado uma reserva de "ana" na "Lab Química" no estado SOLICITADA
    Quando "rui" rejeita a reserva com justificativa "<justificativa>"
    Então a operação é recusada com HTTP 400
    E a reserva permanece no estado SOLICITADA

    Exemplos:
      | justificativa |
      | (nula)        |
      | ""            |
      | "   "         |

  # BDD-R1-08 (RF-18)
  Cenário: Reserva restrita não entra em uso sem ter sido aprovada
    Dado uma reserva de "ana" na "Lab Química" no estado SOLICITADA
    Quando chega o horário de início da reserva / é acionado o início de uso
    Então a reserva NÃO passa a EM_USO
    E permanece no estado SOLICITADA
    # ❓ P-07: o gatilho de início de uso não está definido (ver Regra 4).

  # BDD-R1-09 (RF-19 + RF-32)
  Cenário: Aprovação por Responsável sem autoridade sobre o recurso
    Dado uma reserva restrita da "Lab Química" no estado SOLICITADA
    E o Responsável "lia", que não responde pela "Lab Química"
    Quando "lia" aprova a reserva
    Então ❓ P-02 — o PRD não define se qualquer Responsável aprova qualquer recurso restrito
          ou só o Responsável "competente" por ele.
```

---

## Regra 2 — Solicitante não aprova a própria reserva

**Origem:** RF-19 ("100% das tentativas de aprovação por Solicitante retornam HTTP 403"); RF-03;
persona Solicitante ("Não aprova recursos restritos").

```gherkin
Funcionalidade: Segregação de funções na aprovação

  Contexto:
    Dado a sala restrita "Lab Química"
    E o usuário "ana" com perfil SOLICITANTE
    E uma reserva de "ana" na "Lab Química" no estado SOLICITADA

  # BDD-R2-01 (RF-19)
  Cenário: Solicitante tenta aprovar a própria reserva
    Quando "ana" aprova a própria reserva
    Então a operação é recusada com HTTP 403
    E a reserva permanece no estado SOLICITADA
    E nenhum evento de auditoria "APROVACAO" é registrado

  # BDD-R2-02 (RF-19)
  Cenário: Solicitante tenta rejeitar a própria reserva
    Quando "ana" rejeita a própria reserva com justificativa "Mudei de ideia"
    Então a operação é recusada com HTTP 403
    E a reserva permanece no estado SOLICITADA
    # Desistir por iniciativa própria é cancelamento (RF-14), não rejeição.

  # BDD-R2-03 (RF-19)
  Cenário: Solicitante tenta aprovar reserva de outro solicitante
    Dado uma reserva de "beto" (SOLICITANTE) na "Lab Química" no estado SOLICITADA
    Quando "ana" aprova a reserva de "beto"
    Então a operação é recusada com HTTP 403

  # BDD-R2-04 (RF-38, RNF-06)
  Cenário: Recusa por perfil traz mensagem compreensível
    Quando "ana" aprova a própria reserva
    Então a resposta de erro explica em linguagem natural que só Responsável ou Administrador aprovam
    E a resposta não contém stacktrace nem nome de classe interna

  # BDD-R2-05 — autoaprovação por quem tem perfil aprovador
  Cenário: Responsável que é o solicitante tenta aprovar a própria reserva restrita
    Dado o usuário "rui" com perfil RESPONSAVEL
    E uma reserva restrita criada por "rui" no estado SOLICITADA
    Quando "rui" aprova a própria reserva
    Então ❓ P-01 — o PRD não decide. RF-12 só lista SOL como quem cria reserva, e RF-19 só
          diz que Solicitante não aprova. Não há regra de segregação (quem pede ≠ quem aprova)
          para RES/ADM. O mesmo vale para o Administrador.
```

> **Observação de código (não é critério):** `ReservaService.aprovar` só checa o **perfil**
> (`RESPONSAVEL`/`ADMINISTRADOR`). Não compara `responsavel.getId()` com
> `reserva.getSolicitanteId()`. Hoje, BDD-R2-05 resultaria em aprovação.

---

## Regra 3 — Recurso em manutenção não pode ser reservado

**Origem:** enunciado "Recursos em manutenção não podem ser reservados"; RF-11, RF-21, RF-22,
RF-23; J5; MODELAGEM_DADOS `Manutencao.tipoRecurso ∈ {SALA, PROFESSOR, MATERIAL}`.

```gherkin
Funcionalidade: Bloqueio de reserva por manutenção

  Contexto:
    Dado a sala "Lab 3" não restrita
    E uma manutenção da "Lab 3" de 10/03/2026 08:00:00 até 10/03/2026 18:00:00, motivo "Troca de bancadas"
    E o usuário "ana" com perfil SOLICITANTE

  # BDD-R3-01 (RF-22)
  Cenário: Reserva totalmente dentro da manutenção é recusada
    Quando "ana" cria uma reserva da "Lab 3" de 10/03 10:00 a 12:00
    Então a operação é recusada com HTTP 409
    E a mensagem informa que o recurso está em manutenção no intervalo
    E nenhuma reserva é persistida

  # BDD-R3-02 (RF-22)
  Cenário: Reserva que engloba a manutenção inteira é recusada
    Quando "ana" cria uma reserva da "Lab 3" de 10/03 06:00 a 20:00
    Então a operação é recusada com HTTP 409

  # BDD-R3-03 (RF-13 + RF-22)
  Cenário: Alterar reserva para dentro da manutenção é recusado
    Dado uma reserva APROVADA de "ana" na "Lab 3" em 11/03 10:00 a 12:00
    Quando "ana" altera a reserva para 10/03 10:00 a 12:00
    Então a operação é recusada com HTTP 409
    E a reserva mantém o horário original

  # BDD-R3-04 (RF-11)
  Cenário: Recurso em manutenção não aparece na busca de disponibilidade
    Quando "ana" busca salas disponíveis de 10/03 10:00 a 12:00
    Então "Lab 3" não aparece no resultado

  # BDD-R3-05 (RF-22, modelagem cobre PROFESSOR e MATERIAL)
  Esquema do Cenário: Manutenção/bloqueio vale para qualquer tipo de recurso
    Dado um <tipo> "<recurso>" com bloqueio de 10/03 08:00:00 a 18:00:00
    Quando "ana" cria uma reserva incluindo "<recurso>" de 10/03 10:00 a 12:00
    Então a operação é recusada com HTTP 409

    Exemplos:
      | tipo      | recurso       |
      | SALA      | Lab 3         |
      | MATERIAL  | Projetor P-07 |
      | PROFESSOR | Prof. Lima    |

  # BDD-R3-06 (RF-23)
  Cenário: Cadastrar manutenção sobre reserva APROVADA exige confirmação explícita
    Dado uma reserva APROVADA da "Lab 3" em 12/03 10:00 a 12:00
    Quando o Administrador cadastra manutenção da "Lab 3" de 12/03 09:00 a 13:00 sem confirmar
    Então o cadastro é recusado
    E a reserva continua APROVADA

  # BDD-R3-07 (interação Regra 1 × Regra 3)
  Cenário: Manutenção cadastrada sobre reserva SOLICITADA e aprovação posterior
    Dado uma reserva restrita da "Lab Química" em 13/03 10:00 a 12:00 no estado SOLICITADA
    E o Administrador cadastra manutenção da "Lab Química" de 13/03 08:00 a 18:00
    Quando o Responsável aprova a reserva
    Então ❓ P-04 — o PRD não decide. RF-23 só protege reservas APROVADA/EM_USO, e RF-19 não
          diz se a aprovação revalida manutenção e conflito. Sem decisão, uma reserva pode
          ficar APROVADA dentro de um período de manutenção.
```

### Tabela de exemplos — fronteiras do período de manutenção

Manutenção da "Lab 3": **início `M_i` = 10/03/2026 08:00:00**, **fim `M_f` = 10/03/2026 18:00:00**.
Granularidade dos exemplos: 1 segundo.

```gherkin
  # BDD-R3-08
  Esquema do Cenário: Fronteiras entre reserva e manutenção
    Dado a manutenção da "Lab 3" de 10/03/2026 08:00:00 a 10/03/2026 18:00:00
    Quando "ana" cria uma reserva da "Lab 3" de <inicio> a <fim>
    Então o resultado é <resultado>
```

| Ex. | Início da reserva | Fim da reserva | Relação com a manutenção | Resultado esperado | Fonte da decisão |
|---|---|---|---|---|---|
| F1 | 10/03 06:00:00 | **10/03 08:00:00** | Termina **exatamente** no início (`fim == M_i`) | ❓ **P-03** | PRD não decide |
| F2 | **10/03 18:00:00** | 10/03 20:00:00 | Começa **exatamente** no fim (`início == M_f`) | ❓ **P-03** | PRD não decide |
| F3 | 10/03 06:00:00 | 10/03 08:00:**01** | Cruza **1 s** no início | Recusada — HTTP 409 | RF-22: há sobreposição em qualquer semântica de intervalo |
| F4 | 10/03 17:59:**59** | 10/03 20:00:00 | Cruza **1 s** no fim | Recusada — HTTP 409 | RF-22: idem |
| F5 | 10/03 06:00:00 | 10/03 07:59:59 | Termina 1 s **antes** do início (controle) | Aceita — APROVADA | Sem interseção em qualquer semântica |
| F6 | 10/03 18:00:01 | 10/03 20:00:00 | Começa 1 s **depois** do fim (controle) | Aceita — APROVADA | Sem interseção em qualquer semântica |
| F7 | 10/03 08:00:00 | 10/03 18:00:00 | Intervalo idêntico ao da manutenção | Recusada — HTTP 409 | RF-22 |

Os controles F5/F6 garantem que o teste distingue "encostar" de "cruzar". Sem eles, uma
implementação que recusa tudo num raio de 1 minuto passaria em F3/F4.

> **Observação de código (não é critério):** `ReservaService.sobrepoe` usa
> `inicioA.isBefore(fimB) && inicioB.isBefore(fimA)`, ou seja, intervalo semiaberto `[início, fim)`.
> Hoje F1 e F2 seriam **aceitas**. Isso é uma escolha de implementação, não uma decisão do PRD.
> Também é inconsistente com o cadastro de manutenção (`CadastroRecursoService`), que só
> recebe `Sala`: os exemplos MATERIAL/PROFESSOR de BDD-R3-05 hoje não têm como ser montados.

---

## Regra 4 — Reserva só muda de estado pelas transições permitidas

**Origem:** enunciado "SOLICITADA → APROVADA → EM_USO → CONCLUÍDA" e "Estados alternativos:
rejeitada, cancelada e não compareceu"; "Toda mudança de estado deve gerar auditoria"; RF-14,
RF-18, RF-19, RF-20, RF-27; J9.

### 4.1 Transições permitidas

```gherkin
Funcionalidade: Máquina de estados da Reserva

  # BDD-R4-01 (enunciado, RF-19, RF-20, RF-14, RF-27)
  Esquema do Cenário: Transição permitida muda o estado e gera auditoria
    Dado uma reserva no estado <origem>
    Quando <ator> executa "<acao>"
    Então a reserva passa ao estado <destino>
    E exatamente um evento de auditoria é registrado para essa transição, com o ator e o instante

    Exemplos:
      | origem     | ator        | acao                         | destino        | observação                   |
      | SOLICITADA | Responsável | aprovar                      | APROVADA       | RF-19                        |
      | APROVADA   | ❓ P-07     | iniciar uso                  | EM_USO         | gatilho indefinido           |
      | EM_USO     | ❓ P-07     | concluir                     | CONCLUIDA      | gatilho indefinido           |
      | SOLICITADA | Responsável | rejeitar (com justificativa) | REJEITADA      | RF-20                        |
      | SOLICITADA | Solicitante | cancelar (com motivo)        | CANCELADA      | RF-14                        |
      | APROVADA   | Solicitante | cancelar (com motivo)        | CANCELADA      | RF-14                        |
      | ❓ P-05    | ❓ P-08     | registrar não comparecimento | NAO_COMPARECEU | origem e gatilho indefinidos |
```

### 4.2 Transições proibidas

```gherkin
  # BDD-R4-02 (enunciado: só as transições listadas são válidas)
  Esquema do Cenário: Transição fora do fluxo é recusada e não deixa rastro de mudança
    Dado uma reserva no estado <origem>
    Quando um usuário autorizado executa "<acao>"
    Então a operação é recusada com erro de conflito de estado (❓ P-09 — código HTTP)
    E a reserva permanece no estado <origem>
    E nenhum evento de auditoria de transição é registrado

    Exemplos: pular etapas
      | origem     | acao        | motivo da proibição                     |
      | SOLICITADA | iniciar uso | RF-18: não pula APROVADA                |
      | SOLICITADA | concluir    | não pula APROVADA nem EM_USO            |
      | APROVADA   | concluir    | não pula EM_USO                         |
      | APROVADA   | aprovar     | aprovação só a partir de SOLICITADA     |
      | APROVADA   | rejeitar    | rejeição só a partir de SOLICITADA (J9) |
      | EM_USO     | aprovar     | retrocesso                              |
      | EM_USO     | rejeitar    | J9 só desenha rejeição a partir de SOLICITADA |

    Exemplos: estados terminais não têm saída
      | origem         | acao                                         |
      | CONCLUIDA      | aprovar / iniciar uso / cancelar / rejeitar  |
      | REJEITADA      | aprovar / iniciar uso / cancelar             |
      | CANCELADA      | aprovar / iniciar uso / cancelar             |
      | NAO_COMPARECEU | aprovar / iniciar uso / cancelar / concluir  |

  # BDD-R4-03 (RF-14)
  Cenário: Cancelamento sem motivo é recusado
    Dado uma reserva APROVADA de "ana"
    Quando "ana" cancela a reserva sem informar motivo
    Então a operação é recusada com HTTP 400
    E a reserva permanece APROVADA

  # BDD-R4-04 (RF-03, RF-14)
  Cenário: Solicitante não cancela reserva de terceiro
    Dado uma reserva APROVADA de "beto"
    Quando "ana" cancela a reserva de "beto" com motivo "engano"
    Então a operação é recusada com HTTP 403
    E a reserva permanece APROVADA

  # BDD-R4-05 (RF-30)
  Cenário: Reserva iniciada não pode ser apagada
    Dado uma reserva no estado EM_USO
    Quando qualquer usuário tenta excluí-la
    Então a operação é recusada com HTTP 403 ou 405
    E a reserva continua existindo

  # BDD-R4-06 (RF-14 × J9)
  Cenário: Cancelar reserva EM_USO
    Dado uma reserva no estado EM_USO
    Quando o Solicitante cancela com motivo
    Então ❓ P-06 — RF-14 permite cancelar "em qualquer estado anterior a CONCLUÍDA", o que
          incluiria EM_USO, mas o diagrama J9 não desenha EM_USO → CANCELADA.
```

> **Observações de código (não são critério):**
> - Não existem métodos para `iniciar uso`, `concluir` nem `registrar não comparecimento` em
>   `ReservaService`. As transições APROVADA → EM_USO → CONCLUÍDA e → NAO_COMPARECEU não são
>   exercitáveis hoje.
> - `ReservaService.cancelar` aceita cancelar reserva `EM_USO`. Isso segue RF-14, mas diverge do J9 (P-06).
> - Os testes `MaquinaEstadosReservaTest.estT2/T3/T8/T10–T13` e
>   `AprovacaoRecursosRestritosTest.rf18T1/rf19T4` chamam `aprovar` com um `UUID.randomUUID()`
>   que não existe no `Banco`. O 409 vem do ramo `reserva == null` ("Reserva inexistente"), não
>   da regra de transição. **Eles passam pelo motivo errado** e não protegem a Regra 4. Ao
>   implementar BDD-R4-01/02, os testes precisam montar a reserva no estado de origem real.

---

## Perguntas em aberto (o PRD não decide; não assumir)

| ID | Pergunta | Cenários afetados | Relação com o plano-tdd |
|---|---|---|---|
| P-01 | Um Responsável/Administrador pode aprovar uma reserva restrita que **ele mesmo** criou? Existe regra de segregação "quem pede ≠ quem aprova" para RES/ADM? E, antes disso, RES/ADM podem criar reservas (RF-12 só lista SOL)? | BDD-R2-05 | Nova |
| P-02 | Qualquer Responsável aprova qualquer recurso restrito, ou só o Responsável "competente" por aquele recurso? Como esse vínculo é modelado? | BDD-R1-09 | Mesma raiz de Q23 |
| P-03 | Na fronteira com a manutenção, o intervalo é fechado `[início, fim]` ou semiaberto `[início, fim)`? Reserva que **termina exatamente** no início da manutenção (F1) e reserva que **começa exatamente** no fim (F2) são aceitas? | F1, F2 | Reafirma Q2 (continua aberta) |
| P-04 | Ao aprovar uma reserva SOLICITADA, o sistema deve revalidar manutenção/conflito surgidos depois da criação? Ou o cadastro de manutenção (RF-23) também deve proteger reservas SOLICITADA? | BDD-R3-07 | Nova |
| P-05 | `NAO_COMPARECEU` parte de `APROVADA` ou de `EM_USO`? O diagrama J9 (JORNADAS.md) desenha o ramo saindo de APROVADA; o plano-tdd §4 escreve `EM_USO → NAO_COMPARECEU`. | BDD-R4-01, BDD-R4-02 | Divergência nova; complementa Q25 |
| P-06 | Reserva `EM_USO` pode ser cancelada? RF-14 sugere que sim; J9 não desenha a transição. | BDD-R4-06 | Nova |
| P-07 | O gatilho de `APROVADA → EM_USO` e `EM_USO → CONCLUIDA` é automático (horário/job) ou manual? Quem aciona? | BDD-R1-08, BDD-R4-01, BDD-R4-02 | = Q24 |
| P-08 | Qual o critério e quem registra o não comparecimento (tolerância de atraso, ator)? | BDD-R4-01 | = Q25 |
| P-09 | Qual código HTTP para transição inválida (409? 422?) e é o mesmo para todos os pares proibidos? | BDD-R4-02 | = Q16 |

---

## Classificação: teste de unidade × teste de API

**Critério.** Fica em **unidade** (JUnit 5, `@ParameterizedTest` onde há tabela, sobre
`ReservaService`/`CadastroRecursoService`, sem HTTP) a regra de domínio pura: decisão de estado,
comparação de intervalos e validação de campos. Fica em **API** (caixa-preta via HTTP —
MockMvc/RestAssured com Spring Boot e Testcontainers) o que só se comprova na borda: código HTTP
do perfil autenticado, corpo da mensagem de erro, ausência de stacktrace e efeito persistido e
auditado de ponta a ponta. Algumas regras críticas aparecem nos dois níveis de propósito: a
unidade prova a lógica e a API prova que a borda não a contorna.

> **Pré-requisito:** hoje não existe camada web (nenhum `@RestController` em `src/main`). Os
> testes de API abaixo dependem dela ser criada.

| Cenário | Unidade | API | Justificativa |
|---|:---:|:---:|---|
| BDD-R1-01 nasce SOLICITADA | ✅ | ✅ | Regra de domínio + confirmação do estado devolvido pelo `POST /reservas` |
| BDD-R1-02 nasce APROVADA | ✅ | | Decisão pura de domínio |
| BDD-R1-03 um restrito basta | ✅ | | Decisão pura de domínio |
| BDD-R1-04 Responsável aprova | ✅ | ✅ | Unidade: transição + auditoria. API: perfil autenticado autorizado |
| BDD-R1-05 Administrador aprova | ✅ | ✅ | Idem |
| BDD-R1-06 rejeita com justificativa | ✅ | ✅ | API confirma que a justificativa fica visível ao Solicitante |
| BDD-R1-07 rejeição sem justificativa (parametrizado) | ✅ | ✅ | Unidade: validação. API: HTTP 400 por validação de entrada (RNF-04) |
| BDD-R1-08 restrita não entra em uso sem aprovação | ✅ | | Transição de domínio (bloqueado por P-07) |
| BDD-R1-09 Responsável não competente | ⏸ | ⏸ | Aguarda P-02 |
| BDD-R2-01 Solicitante aprova a própria → 403 | ✅ | ✅ | **Crítico.** Unidade: regra de perfil. API: autorização na borda (RF-02/RF-19) |
| BDD-R2-02 Solicitante rejeita a própria → 403 | ✅ | ✅ | Idem |
| BDD-R2-03 Solicitante aprova a de terceiro → 403 | | ✅ | Autorização por perfil autenticado; a unidade já está coberta por R2-01 |
| BDD-R2-04 mensagem compreensível, sem stacktrace | | ✅ | Só observável no corpo HTTP (RF-38/RNF-06) |
| BDD-R2-05 autoaprovação por RES/ADM | ⏸ | ⏸ | Aguarda P-01 |
| BDD-R3-01 dentro da manutenção → 409 | ✅ | ✅ | Unidade: regra. API: 409 + mensagem + nada persistido |
| BDD-R3-02 engloba a manutenção | ✅ | | Variação de intervalo |
| BDD-R3-03 alterar para dentro da manutenção | ✅ | | Revalidação no domínio |
| BDD-R3-04 fora da busca de disponibilidade | ✅ | ✅ | Unidade: filtro. API: resposta de `GET` de disponibilidade |
| BDD-R3-05 manutenção por tipo de recurso (parametrizado) | ✅ | | Regra de domínio por tipo |
| BDD-R3-06 manutenção sobre APROVADA exige confirmação | ✅ | ✅ | API: fluxo de confirmação explícita do Administrador |
| BDD-R3-07 aprovação após manutenção | ⏸ | ⏸ | Aguarda P-04 |
| BDD-R3-08 F3, F4, F5, F6, F7 (fronteiras) | ✅ | | `@ParameterizedTest`/`@CsvSource` sobre a comparação de intervalos. Precisão de segundo é lógica pura e na API seria lento e redundante |
| BDD-R3-08 F1, F2 (encostar exatamente) | ⏸ | | Aguarda P-03. Depois de decidido, vira linha do mesmo `@CsvSource` |
| BDD-R4-01 transições permitidas (parametrizado) | ✅ | | Tabela de transição: `@ParameterizedTest` montando a reserva **no estado de origem real** (linhas com P-05/P-07/P-08 aguardam decisão) |
| BDD-R4-01 auditoria por transição | ✅ | ✅ | API (com Testcontainers) prova que auditoria e transição persistem juntas (RF-27) |
| BDD-R4-02 transições proibidas (parametrizado) | ✅ | | Tabela de pares proibidos. O código HTTP aguarda P-09 |
| BDD-R4-03 cancelar sem motivo → 400 | ✅ | ✅ | Validação de entrada também na borda |
| BDD-R4-04 cancelar reserva de terceiro → 403 | ✅ | ✅ | Autorização (RF-03) |
| BDD-R4-05 reserva iniciada não pode ser apagada | | ✅ | É sobre o verbo `DELETE` exposto (RF-30; ver também Q22) |
| BDD-R4-06 cancelar EM_USO | ⏸ | | Aguarda P-06 |

**Resumo:** testes de **unidade** cobrem a lógica de estado, intervalos e validação, incluindo
toda a tabela de fronteiras e as duas tabelas de transição. Testes de **API** cobrem autorização
por perfil (403), validação na borda (400), conflito e mensagem (409, RF-38/RNF-06), exclusão
bloqueada e a garantia de que a transição e a auditoria persistem juntas. ⏸ = cenário bloqueado
por pergunta em aberto, sem teste até a decisão.
