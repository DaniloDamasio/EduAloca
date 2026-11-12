# DEF-015 — CRUD de sala, professor e material sem edição, sem inativação e com validação parcial

| Campo | Valor |
|---|---|
| Status | **Aberto** (red registrado; correção em andamento) |
| Severidade | Alta: RF-04, RF-05 e RF-06 são Críticos e pedem "criação/edição/inativação refletida na consulta" e "100% dos campos obrigatórios validados"; nenhuma das três entidades pode ser editada |
| Prioridade | Alta |
| Requisitos | RF-04, RF-05, RF-06, RF-28 |
| Componente | `CadastroRecursoService`, `Sala`, `Professor`, `Material` |
| Encontrado em | 11/11/2026, auditoria de RF-04/05/06 para a RTM ([mutacao-manual.md §6](../testes/mutacao-manual.md)), commit `49f05f6` |
| Teste que detecta | `CrudRecursoTest` (3 grupos: sala, professor, material) |

## Resumo

| Operação | Sala | Professor | Material |
|---|---|---|---|
| Criar | sim | sim | sim |
| Editar | **não existe** | **não existe** | **não existe** |
| Inativar | sim | **não existe** (só `Professor.inativar()`, sem serviço nem auditoria) | **não existe** (idem) |
| Campos obrigatórios validados | nome, capacidade; **tipo e localização não** | identificador único; **nome e identificador vazios não** | quantidade; **nome e tipo não** |

Os testes de `CadastroConsultaTest` passavam porque cobrem só o que existe.

## Passos para reproduzir

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=CrudRecursoTest
```

## Resultado esperado

- `atualizarSala`, `atualizarProfessor` e `atualizarMaterial` alteram o recurso (mesmo objeto, para que reservas e
  manutenções continuem apontando para ele), e a mudança aparece na busca. Id inexistente: 404.
- `inativarProfessor` e `inativarMaterial` tiram o recurso da busca. Id inexistente: 404.
- Campo obrigatório ausente ou em branco, na criação ou na edição: 400 com o nome do campo (RF-38). A edição recusada
  não altera nada.
- Cada edição e inativação gera exatamente um evento de auditoria (`ATUALIZACAO`/`INATIVACAO`, RF-28).

## Resultado obtido

Falha de compilação: `atualizarSala`, `atualizarProfessor`, `atualizarMaterial`, `inativarProfessor` e
`inativarMaterial` não existem em `CadastroRecursoService`.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- Teste de unidade sobre `CadastroRecursoService` e `PesquisaRecursoService`

## Premissas

- Campos obrigatórios: sala (nome, tipo, localização, capacidade > 0); professor (nome, identificador, lista de
  competências não nula, **podendo ser vazia**, como já faziam os testes `rf05T1`/`rf05T2`); material (nome, tipo,
  quantidade > 0). O indicador de restrição é `boolean` e está sempre presente.
- O identificador institucional do professor **não é editável**: é a chave de unicidade (RF-05-T2).
- Não há rota HTTP de cadastro na API atual (só `/manutencoes`). A correção fica no serviço de domínio, como o
  restante do cadastro.
