# DEF-016 — Busca e criação divergem na quantidade de material

| Campo | Valor |
|---|---|
| Status | **Corrigido** (opção A, decidida em 12/11/2026 pelo autor; reteste aprovado em 12/11/2026, depois da rodada 02 de carga) |
| Severidade | Média: a busca oferece um material que a criação recusa com 409. Não gera reserva indevida nem dupla reserva, mas quebra o fluxo do Solicitante e contradiz o RF-11 ("0% de recursos indisponíveis retornados") |
| Prioridade | Alta para a demonstração: aparece ao reservar o mesmo material em duas reservas no mesmo horário |
| Requisitos | RF-10, RF-11, RF-15 (regra crítica "reservas do mesmo recurso não podem se sobrepor") |
| Componente | `PesquisaRecursoService.buscarMateriais` × `ReservaRepositorio.existeConflito` e constraint `ex_reserva_recurso_sem_sobreposicao` |
| Encontrado em | 12/11/2026, ao escrever o teste de sobreposição material × material (pendência P5 do [status-requisitos](../status-requisitos.md)); a divergência já tinha sido apontada na auditoria de 10/11 (seção 3) |
| Teste que detecta | `BuscaMaterialExclusivaTest` (10 casos) e `PesquisaDisponibilidadeTest.rf10T2_materialParcialmenteReservado_naoApareceNaBusca`; a regra de criação continua fixada por `SobreposicaoMaterialTest` |

## Resumo

A busca trata o material **por quantidade**: um material com 5 unidades e 1 reservada aparece disponível para mais
unidades no mesmo horário (`PesquisaDisponibilidadeTest.rf10T2`, que assume a soma de reservas parciais, gap Q12). A
criação trata o material como **exclusivo**: qualquer reserva ativa sobreposta do mesmo material bloqueia, qualquer que
seja a quantidade. Isso vale no repositório em memória (`existeConflito`) e no PostgreSQL
(`EXCLUDE USING gist (tipo_recurso WITH =, recurso_id WITH =, periodo WITH &&)`).

## Passos para reproduzir

1. Administrador cadastra "Projetor" com quantidade total 5.
2. Solicitante A reserva 1 projetor das 09:00 às 11:00.
3. Solicitante B busca materiais do tipo projetor, quantidade 1, das 09:00 às 11:00: o projetor **aparece**.
4. Solicitante B adiciona o projetor e solicita a reserva: **409** "Recurso indisponivel: sobreposicao com reserva ativa".

## Resultado esperado

A busca e a criação aplicam a mesma regra: ou o material some da busca, ou a reserva é aceita.

## Resultado obtido

A busca oferece o material e a criação recusa.

## Opções de correção (decisão pendente)

| Opção | O que muda | Impacto |
|---|---|---|
| **A. Material exclusivo** (alinhar a busca à criação) | `buscarMateriais` passa a excluir material com reserva ativa sobreposta; `quantidade` só limita o pedido a `quantidadeTotal` | Pequeno e seguro: a regra do banco e a proteção de concorrência continuam iguais. `rf10T2` muda de expectativa (assumia a soma de parciais). Perde-se o uso de "várias unidades do mesmo item" por reservas diferentes |
| **B. Material por quantidade** (alinhar a criação à busca) | Criação aceita enquanto a soma das quantidades sobrepostas ≤ `quantidadeTotal` | Grande: a constraint de exclusão deixa de valer para material e a proteção contra dupla reserva precisa de outro mecanismo (lock pessimista ou contador por intervalo), com novos testes de concorrência em PostgreSQL |

Recomendação: **A** para a entrega, com a limitação registrada; **B** como evolução, se o uso por unidades for
necessário.

## Decisão (12/11/2026)

**Opção A: material exclusivo no intervalo.** A busca passa a seguir a mesma regra da criação e da constraint do banco.
A opção B fica registrada como evolução possível, fora do escopo da entrega.

Correção (feita depois da rodada 02 de carga, para não disparar recompilação na máquina durante a medição):

1. `PesquisaRecursoService.buscarMateriais`: excluir material com reserva ativa (`SOLICITADA`, `APROVADA`, `EM_USO`)
   sobreposta, como já é feito para sala e professor; manter o filtro de manutenção; `quantidade` só exige
   `quantidade <= quantidadeTotal`.
2. `PesquisaDisponibilidadeTest.rf10T2`: muda de expectativa. Com 2 unidades reservadas no intervalo, o material
   **não** aparece (antes: aparecia para 1 unidade, pela soma de parciais do gap Q12).
3. Teste novo do DEF-016 (red antes, green depois): material com reserva sobreposta some da busca, aparece de novo quando
   a reserva é cancelada ou quando o intervalo só encosta, e o que a busca oferece a criação aceita.
4. Reteste: suíte completa (`./mvnw -B verify`) e registro do resultado neste arquivo.

## Reteste

- **Red:** com os testes novos e o `rf10T2` reescrito (o antigo nem reservava nada e só conferia lista não vazia), 5
  falhas: `BuscaMaterialExclusivaTest` (material com 1 de 5 unidades reservadas aparecia na busca; bordas sobrepostas;
  "busca e criação concordam") e `rf10T2`.
- **Green:** `buscarMateriais` passou a usar o mesmo `disponivel()` de sala e professor (reserva ativa sobreposta ou
  manutenção tiram o material da busca) e a exigir só `quantidade <= quantidadeTotal`; o cálculo de unidades livres
  saiu. `BuscaMaterialExclusivaTest` 10/10, `PesquisaDisponibilidadeTest` 12/12, `SobreposicaoMaterialTest` 10/10,
  `BuscaRecursoEmManutencaoTest` 3/3, `InterfacePerfisApiTest` (busca de material pela API) verde.
- Suíte completa: ver o `verify` final registrado no [relatório de qualidade](../relatorio-qualidade.md) §10.

## Limite

A opção A tira o uso de "várias unidades do mesmo item por reservas diferentes no mesmo horário". Se for necessário, a
opção B continua descrita acima como evolução.

## Ambiente

- Java 21, Spring Boot 4.1.1, perfil padrão em memória e PostgreSQL 16 (Testcontainers)

## Histórico

A divergência foi confirmada por leitura de código e pelos testes existentes (`rf10T2` e a constraint). O teste
`SobreposicaoMaterialTest` foi escrito em 12/11/2026 e executado depois da
rodada 02 de carga.
