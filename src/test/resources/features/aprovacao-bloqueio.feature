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
# Linhas de exemplo bloqueadas ficam aqui com a tag @bloqueado e nao rodam
# (cucumber.filter.tags em src/test/resources/junit-platform.properties).

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
