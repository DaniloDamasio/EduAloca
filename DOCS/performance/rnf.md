# Requisitos de desempenho: consulta de disponibilidade e criação de reserva

> **Status: vigente (atualizado em 13/11/2026).** Trabalho individual: as metas "-P" abaixo foram definidas pelo autor e
> usadas como critério nas rodadas [01](resultado-01.md) e [02](resultado-02.md). Elas **complementam** RNF-01, RNF-02 e
> RNF-03 de [REQUISITOS.md](../REQUISITOS/REQUISITOS.md), que continuam sendo os critérios oficiais. Os resultados são
> sempre comparados com os dois. As decisões que antes estavam em aberto estão na seção 6.

## 1. Relação com os requisitos atuais

| Atual | Texto atual | Proposta | O que muda |
|---|---|---|---|
| RNF-01 | Consulta: p95 ≤ 2 s com até 50 usuários simultâneos | **RNF-01-P** | Meta mais apertada (300 ms) e operação nomeada (`GET /salas/disponiveis`) |
| RNF-02 | Escrita de reserva: p95 ≤ 3 s sob a mesma carga | **RNF-02-P** | Meta mais apertada (500 ms), com disputa pelo mesmo recurso |
| RNF-03 | Pico ≥ 100 req/min em criação, sem 5xx | **RNF-03-P** | Mantém "zero 5xx" e acrescenta uma regra de correção: nenhuma dupla reserva |

Por que apertar: 2 s e 3 s são limites de "não travar", não de boa experiência. A consulta é feita
várias vezes antes de cada reserva, e um p95 de 300 ms deixa a busca com sensação de resposta
imediata. Para avaliar só contra 2 s / 3 s, o plano JMeter continua válido; basta mudar o critério de aceite.

## 2. Requisitos propostos

### RNF-01-P: consulta de disponibilidade

- **Operação:** `GET /salas/disponiveis?inicio=…&fim=…` com token Bearer válido.
- **Carga:** modelo de carga da seção 3 (50 usuários simultâneos).
- **Critério de aceite, medido na fase de carga estável (ramp-up excluído):**
  - p95 do tempo de resposta **≤ 300 ms**;
  - taxa de erro **< 1 %** (erro = status diferente de 200, ou falha de conexão).

### RNF-02-P: criação de reserva com disputa

- **Operação:** `POST /reservas`, com todos os usuários disputando **a mesma sala** em janelas de
  horário compartilhadas.
- **Critério de aceite, medido na fase de carga estável:**
  - p95 do tempo de resposta **≤ 500 ms**, contando juntas as respostas 201 e 409;
  - taxa de erro **< 1 %**.
- **Respostas esperadas:** `201 Created` (venceu a disputa) ou `409 Conflict` (horário já ocupado).
  Na disputa, **409 é resultado correto e não conta como erro**. Qualquer outro status conta como erro.

Por que 500 ms para escrita e não 300 ms: a criação passa pela validação de conflito e pela gravação
com a constraint `EXCLUDE` do PostgreSQL (DEF-001), que são mais caras que uma leitura. Para uma
meta única de 300 ms para as duas operações, como no exemplo do enunciado, basta trocar o
número aqui.

### RNF-03-P: robustez e correção sob disputa (obrigatório, independe dos tempos)

- **Nenhuma** resposta 5xx em `POST /reservas` durante toda a rodada (zero tolerância). Um 500 na
  disputa indica condição de corrida não tratada.
- **Nenhuma dupla reserva:** no máximo uma resposta 201 por (sala, janela de horário). Isso é
  conferido por asserção no próprio plano.
- A vazão de criação durante a fase estável deve ficar **≥ 100 req/min**, para continuar cobrindo o
  RNF-03 original.

## 3. Modelo de carga

| Parâmetro | Valor proposto | Propriedade JMeter (`-J`) | Justificativa |
|---|---|---|---|
| Usuários virtuais simultâneos | **50** | `USUARIOS=50` | Mesmo número dos RNF-01/02 atuais |
| Ramp-up | **60 s** (cerca de 1 usuário novo a cada 1,2 s) | `RAMPUP=60` | Evita o "pico de partida" e deixa a JVM aquecer (JIT) antes da medição |
| Duração total | **6 min** = 1 min de ramp-up + **5 min de carga estável** | `DURACAO=360` | 5 min estáveis geram milhares de amostras, o bastante para um p95 confiável |
| Tempo de pensamento | 1 a 2 s (uniforme) entre requisições | `PENSAR_MS=1000`, `PENSAR_VAR_MS=1000` | Simula alguém lendo a lista de salas antes de agir |
| Login | 1 vez por usuário virtual, no início | (fixo) | O token é reaproveitado, como num cliente real |

### Mistura de operações (por iteração de cada usuário)

| Operação | Frequência | Observação |
|---|---|---|
| `POST /auth/login` | 1× por usuário (não entra na mistura) | Token extraído por JSON Extractor (`$.token`) |
| `GET /salas/disponiveis` | 100 % das iterações | Consulta a janela de horário da iteração |
| `POST /reservas` | **25 %** das iterações (`PCT_CRIACAO=25`) | Mesma sala para todos, na mesma janela consultada |

Resultado esperado: **cerca de 80 % consultas e 20 % criações**. Como o tempo de pensamento
(média de 1,5 s) vale para cada requisição, uma iteração leva em média cerca de 1,9 s. Com 50
usuários, isso dá aproximadamente 26 consultas/s e 6,5 criações/s (cerca de 390 criações/min, acima
dos 100/min do RNF-03).

### Como a disputa é produzida

- Todos os usuários tentam reservar **a mesma sala** (`SALA_ID`, por padrão a sala fixa do seed
  `00000000-0000-0000-0000-000000000001`).
- Um contador global de iterações define a janela: a cada `DISPUTA_POR_JANELA` iterações (padrão
  **40**) o plano avança uma janela de 1 hora. Com 25 % de criação, cerca de **10 usuários disputam
  cada janela**: só um pode receber 201 e os demais devem receber 409.
- As janelas começam 30 dias após o início da rodada, para não colidir com a manutenção de exemplo do
  seed nem com o relógio da aplicação.

## 4. Ambiente e regras de execução

- **Somente a aplicação local** (`localhost`). O plano tem uma trava: se `HOST` não for `localhost`,
  `127.0.0.1` ou `::1`, a rodada é abortada antes de enviar qualquer requisição. **Nunca rodar contra
  ambiente compartilhado.**
- **Somente modo CLI** (sem GUI, que distorce as medições):

  ```bash
  jmeter -n -t perf/reservas.jmx -l perf/resultados/rodada-01.jtl -e -o perf/relatorio-01
  ```

- **Ambiente limpo por rodada:** reiniciar a aplicação (e recriar o banco, se for PostgreSQL) antes
  de cada rodada, para que reservas de rodadas anteriores não transformem tudo em 409.
- **Persistência:** a rodada de referência usa PostgreSQL local em container (mesma garantia de
  concorrência validada em DEF-001). Uma rodada em memória serve só como comparação.
- **Usuários fictícios:** [perf/usuarios.csv](../../perf/usuarios.csv). São contas de teste sem
  dados reais, que existem apenas no perfil `perf` da aplicação.
- **Versionamento:** o plano (`perf/reservas.jmx`) e o CSV são versionados. O relatório HTML
  (`perf/relatorio-*/`) e os brutos (`perf/resultados/`) **não** são versionados; o resumo de cada
  rodada é registrado na seção 5.

### Como subir o ambiente local da rodada

```bash
# PostgreSQL descartável, acessível só pelo loopback
docker run -d --name edualoca-perf-pg -e POSTGRES_DB=edualoca -e POSTGRES_USER=edualoca \
  -e POSTGRES_PASSWORD=perf-local -p 127.0.0.1:55432:5432 postgres:16-alpine

./mvnw -q -DskipTests package
java -Xms512m -Xmx512m -jar target/edualoca-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=perf --server.address=127.0.0.1 \
  --edualoca.persistencia.url=jdbc:postgresql://127.0.0.1:55432/edualoca \
  --edualoca.persistencia.usuario=edualoca --edualoca.persistencia.senha=perf-local

# depois da rodada: parar a aplicação e remover o banco
docker rm -f edualoca-perf-pg
```

## 5. Registro de rodadas

Métricas da **fase estável** (os primeiros 60 s de ramp-up foram descartados), calculadas a partir
do `.jtl`.

| Rodada | Data | Ambiente | Consulta p95 | Criação p95 | Erro consulta | Erro criação | 5xx | Duplas | Veredito |
|---|---|---|---|---|---|---|---|---|---|
| 01 | 2026-11-10 | Notebook (macOS, JDK 21), app + PostgreSQL 16 local, JMeter 5.6.3 na mesma máquina | **2 ms** | **8 ms** | 0,00 % | 0,00 % | 0 | 0 | Atende à proposta (com a ressalva abaixo) |
| **02 (vigente)** | 2026-11-12 | Mesmo ambiente; commit `38bbc04`; 8 cenários de 50 a 400 usuários, incluindo 10 salas | **2–3 ms** | **8–12 ms** | 0,00 % | 0,00 % | 0 | 0 | Atende, sem saturar até 400 usuários ([resultado-02.md](resultado-02.md)) |

**Rodada 02:** ver [resultado-02.md](resultado-02.md). Escada 50 → 400 usuários, variante com a disputa espalhada por
10 salas, 3 repetições de 50 usuários e coleta de CPU, memória, GC e pool. Vazão de até 264,5 req/s (3.174 criações/min),
ainda limitada pelo tempo de pensamento do modelo, não pelo sistema. 6.308 janelas disputadas, todas com exatamente
uma reserva aceita.

**Rodada 01, detalhes:**

- 10.914 amostras em 6 min (cerca de 30 req/s no total). Fase estável: 7.906 consultas (26,5/s) e
  1.977 criações (6,6/s, cerca de 395/min, acima dos 100/min do RNF-03).
- Consulta: p50 1 ms, p95 2 ms, p99 3 ms, máximo 13 ms. Criação: p50 4 ms, p95 8 ms, p99 10 ms,
  máximo 14 ms.
- Disputa: 2.166 criações no total, sendo **217 respostas 201 e 1.949 respostas 409** (cerca de 10
  tentativas por janela, como planejado). Houve 217 janelas com vencedor e 217 linhas na tabela
  `reserva`: **nenhuma dupla reserva** e nenhum status fora de {201, 409}.
- Mistura real: 80 % consultas e 20 % criações, como no modelo.

**Ressalvas:**

1. **Ambiente otimista.** Gerador de carga, aplicação e banco rodaram na mesma máquina, sem rede e com
   o banco quase vazio. Os tempos em milissegundos mostram que a aplicação não é gargalo nessa carga,
   mas **não representam produção**. A proposta de 300/500 ms continua sendo a meta para um ambiente
   com rede real.
2. **50 usuários não estressam o sistema.** A rodada confirma a meta, mas não mostra onde está o
   limite. Próximo passo sugerido: uma rodada de estresse (por exemplo, 100, 200 e 400 usuários) para
   achar o ponto de saturação.
3. A consulta de disponibilidade lê o espelho em memória (`Banco.reservas`) que o repositório JDBC
   mantém. Numa instância única ela fica coerente com o banco; com várias instâncias, a busca de uma
   instância não enxerga reservas criadas pelas outras (já registrado no Javadoc de
   `ReservaRepositorioJdbc`). A criação continua protegida pela constraint `EXCLUDE`.

## 6. Decisões (antes "pontos para o grupo decidir")

| Ponto | Decisão |
|---|---|
| Aceitar 300 ms (consulta) e 500 ms (criação), ou manter 2 s / 3 s? | Os dois: RNF-01 a RNF-03 continuam os critérios oficiais, e RNF-01-P a RNF-03-P são metas internas, mais exigentes. A [rodada 02](resultado-02.md) atende a ambos |
| A mistura 80/20 representa o uso esperado? | Mantida: não há dado de uso real. A rodada 02 também mediu disputa concentrada (sala única e 10 salas) |
| 50 usuários simultâneos é o pico esperado? | Mantido como referência, por ser o número dos RNFs. A rodada 02 foi além e mediu 100, 200 e 400 usuários para procurar saturação, sem encontrá-la |
| Substituir RNF-01/02/03 em REQUISITOS.md? | Não. As metas "-P" ficam neste documento como complementares |
