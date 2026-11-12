# language: pt
#
# Especificacao executavel dos cenarios de API de DOCS/testes/bdd-aprovacao-bloqueio.md
# (coluna "API" da tabela de classificacao). O texto dos passos e o mesmo do documento.
# Cada "Funcionalidade" do documento virou uma "Regra" desta feature, com o seu proprio
# "Contexto", porque o Gherkin aceita uma unica Funcionalidade por arquivo.
#
# Fora desta feature, de proposito:
#   - cenarios so de unidade (R1-02, R1-03, R1-08, R3-02, R3-03, R3-05, R3-08, R4-02):
#     cobertos em BddAprovacaoBloqueioTest e nas suites JUnit;
#   - cenarios bloqueados por pergunta em aberto (R1-09/P-02, R2-05/P-01, R3-07/P-04, R4-06/P-06):
#     o passo "Entao" deles e uma pergunta, nao um resultado verificavel.
# Linhas de exemplo bloqueadas ficariam aqui com a tag @bloqueado e nao rodariam
# (cucumber.filter.tags em src/test/resources/junit-platform.properties). Desde 12/11/2026 nenhuma linha esta
# bloqueada: as decisoes P-05, P-07 e P-08 liberaram iniciar uso, concluir e nao comparecimento em BDD-R4-01.

Funcionalidade: Aprovação, autoaprovação, bloqueio por manutenção e máquina de estados da Reserva

  Regra: Regra 1 — Recurso restrito exige aprovação de um Responsável

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

  Regra: Regra 2 — Solicitante não aprova a própria reserva

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

  Regra: Regra 3 — Recurso em manutenção não pode ser reservado

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

    # BDD-R3-04 (RF-11)
    Cenário: Recurso em manutenção não aparece na busca de disponibilidade
      Quando "ana" busca salas disponíveis de 10/03 10:00 a 12:00
      Então "Lab 3" não aparece no resultado

    # BDD-R3-06 (RF-23)
    Cenário: Cadastrar manutenção sobre reserva APROVADA exige confirmação explícita
      Dado uma reserva APROVADA da "Lab 3" em 12/03 10:00 a 12:00
      Quando o Administrador cadastra manutenção da "Lab 3" de 12/03 09:00 a 13:00 sem confirmar
      Então o cadastro é recusado
      E a reserva continua APROVADA

  Regra: Regra 4 — Reserva só muda de estado pelas transições permitidas

    # BDD-R4-01 (enunciado, RF-19, RF-20, RF-14, RF-27)
    Esquema do Cenário: Transição permitida muda o estado e gera auditoria
      Dado uma reserva no estado <origem>
      Quando <ator> executa "<acao>"
      Então a reserva passa ao estado <destino>
      E exatamente um evento de auditoria é registrado para essa transição, com o ator e o instante

      Exemplos:
        | origem     | ator        | acao                         | destino        | observação                   |
        | SOLICITADA | Responsável | aprovar                      | APROVADA       | RF-19                        |
        | SOLICITADA | Responsável | rejeitar (com justificativa) | REJEITADA      | RF-20                        |
        | SOLICITADA | Solicitante | cancelar (com motivo)        | CANCELADA      | RF-14                        |
        | APROVADA   | Solicitante | cancelar (com motivo)        | CANCELADA      | RF-14                        |
        | APROVADA   | Responsável | iniciar uso                  | EM_USO         | RF-18, P-07 (manual)         |
        | EM_USO     | Responsável | concluir                     | CONCLUIDA      | P-07 (manual)                |
        | APROVADA já iniciada há 15 min | Responsável | registrar não comparecimento | NAO_COMPARECEU | P-05 (de APROVADA), P-08 (início + 15 min) |

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
