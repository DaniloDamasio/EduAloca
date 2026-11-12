# DEF-015 — CRUD de sala, professor e material sem edição, sem inativação e com validação parcial

| Campo | Valor |
|---|---|
| Status | **Corrigido** (reteste aprovado em 11/11/2026; red `fd4d18c`, green `2c9b98c`) |
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

## Correção

Commit `2c9b98c`.

- `Sala`, `Professor` e `Material` ganharam `atualizar(...)`, que altera o próprio objeto. Reservas, manutenções e
  buscas comparam recursos por referência, então trocar a instância quebraria o vínculo.
- `CadastroRecursoService`: `atualizarSala`, `atualizarProfessor`, `atualizarMaterial`, `inativarProfessor` e
  `inativarMaterial`. Cada um responde 404 para id inexistente, valida antes de alterar e grava um evento
  `ATUALIZACAO` ou `INATIVACAO`.
- A validação de campos obrigatórios foi centralizada (`validarSala`, `validarProfessor`, `validarMaterial`) e vale
  igual para criação e edição. As mensagens nomeiam o campo.

## Reteste

- `CrudRecursoTest` verde (13 casos nos 3 grupos). `./mvnw -B verify` local com Docker: 419 unitários (3 pulados) e
  14 ITs, gate JaCoCo atendido.
- Mutação: 24 de 24 mutantes de cadastro mortos (C1–C24, [mutacao-manual.md §6](../testes/mutacao-manual.md)).

## Limite

- Reduzir a quantidade de um material abaixo do que já está reservado é aceito. Não há regra nos requisitos, e o
  caso fica como pendência de PRD.
- Os eventos de cadastro continuam com `autorId = null`: o serviço não recebe o usuário (risco A-14 do relatório).
- Sem rota HTTP e sem checagem de perfil no serviço. A restrição ao Administrador (RF-04/05/06) só existirá quando
  houver rota.

## Premissas

- Campos obrigatórios: sala (nome, tipo, localização, capacidade > 0); professor (nome, identificador, lista de
  competências não nula, **podendo ser vazia**, como já faziam os testes `rf05T1`/`rf05T2`); material (nome, tipo,
  quantidade > 0). O indicador de restrição é `boolean` e está sempre presente.
- O identificador institucional do professor **não é editável**: é a chave de unicidade (RF-05-T2).
- Não há rota HTTP de cadastro na API atual (só `/manutencoes`). A correção fica no serviço de domínio, como o
  restante do cadastro.
