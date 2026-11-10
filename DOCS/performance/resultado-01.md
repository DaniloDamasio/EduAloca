# Resultado da rodada 01: consulta de disponibilidade e criação de reserva sob disputa

- **Data da rodada:** 2026-11-10, das 16:29:01 às 16:35:01 (UTC−3)
- **Plano:** [rnf.md](rnf.md) (requisitos, seções 2 a 4) e [perf/reservas.jmx](../../perf/reservas.jmx)
- **Brutos:** `perf/resultados/rodada-01.jtl`, `perf/resultados/jmeter-rodada-01.log`,
  `perf/resultados/rodada-01-banco.sql` (dump do banco da rodada) e o relatório HTML
  `perf/relatorio-01/`. Pela regra do plano, nenhum desses arquivos é versionado; os hashes estão na
  seção 1.4.
- **Análise:** números recalculados a partir do `.jtl` (script Python, percentil *nearest-rank* sobre
  o `elapsed` em ms inteiros), e não copiados do relatório HTML.

> **Escopo desta conclusão.** Tudo abaixo vale **para esta máquina, esta versão e esta carga**.
> Gerador de carga, aplicação e banco rodaram no mesmo notebook, sem rede entre eles e com o banco
> praticamente vazio. Estes números **não permitem afirmar nada sobre produção**: nem que lá vai
> atender, nem com que margem.

## 1. Ambiente

### 1.1 Máquina

| Item | Valor |
|---|---|
| Equipamento | Apple Mac16,8, Apple M4 Pro, 12 núcleos, 24 GiB de RAM |
| Sistema | macOS 26.7.2 (build 25G332) |
| Docker | Engine 29.5.2, VM Linux com **2 vCPUs e 3,8 GiB** (é onde o PostgreSQL rodou) |
| PostgreSQL | 16.15 (`postgres:16-alpine`, `sha256:aa6cb33cf4c1…`), em container, porta 55432 no loopback |
| JMeter | 5.6.3, modo CLI, na mesma máquina (JDK 21.0.13, heap máx. 1 GiB) |
| Outros processos | Não controlados nem registrados (notebook de uso pessoal) |

### 1.2 Aplicação

| Item | Valor |
|---|---|
| Artefato | `target/edualoca-0.0.1-SNAPSHOT.jar` (Spring Boot 4.1.1), gerado às 16:28:28 |
| Commit base | `0cf9518` (branch `concorrencia-reservas-testcontainers`) |
| **Alterações fora de commit no build** | `ApiConfig.java` (bean `AutenticacaoService`), `LoginController.java` (novo, `POST /auth/login`) e `SeedUsuariosPerf.java` (novo, usuários do perfil `perf`). As três classes estão dentro do jar. |
| Perfil / persistência | `perf`, `ReservaRepositorioJdbc` sobre PostgreSQL, pool Hikari com o padrão de 10 conexões |
| JVM da aplicação | **Não registrada.** O plano prevê `-Xms512m -Xmx512m`, mas o comando efetivamente usado não ficou salvo. O JDK no PATH é o Temurin 21.0.9. |

**Consequência:** a versão medida **não corresponde a nenhum commit**. A rodada não pode ser
reproduzida a partir do git até que essas três alterações sejam commitadas (ver seção 6).

### 1.3 O que não foi coletado

- CPU, memória e GC da aplicação, CPU do container do banco e uso do pool Hikari durante a rodada.
  Sem esses dados, a hipótese de gargalo da seção 4 se apoia **só em tempos de resposta**.
- Uma segunda execução nas mesmas condições: com uma rodada só, não há medida de variação entre
  rodadas.

### 1.4 Integridade dos artefatos (SHA-256, primeiros 16 caracteres)

| Arquivo | Hash |
|---|---|
| `perf/resultados/rodada-01.jtl` | `dcfeada7c825bdee` |
| `perf/reservas.jmx` | `e07b3d46b84c38e6` |
| `perf/usuarios.csv` | `a4b336ba2b8cb3d9` |
| `target/edualoca-0.0.1-SNAPSHOT.jar` | `9ab5929e6a43c442` |

## 2. Carga aplicada

| Parâmetro | Planejado ([rnf.md §3](rnf.md)) | Observado |
|---|---|---|
| Usuários virtuais | 50 | 50 (`allThreads` ≤ 50 em toda a rodada) |
| Ramp-up | 60 s | 1 thread a cada ~1,2 s; a 50ª começou em 58,8 s |
| Duração | 360 s | 360 s (última amostra iniciada em 359,97 s) |
| Pensamento | 1.000 a 2.000 ms, uniforme | não aparece no `.jtl`; a vazão bate com 1,5 s de média (seção 4) |
| Mistura | ~80 % consulta / ~20 % criação | 79,97 % / 20,02 % na fase estável |
| Disputa | 1 sala, ~10 tentativas por janela de 1 h | 217 janelas, de 6 a 11 tentativas cada (mediana 10, média 9,98) |
| Alvo | somente localhost | trava confirmada no log: `Trava OK: alvo http://localhost:8080` |

**Fase estável** = de 60 s a 360 s depois do início do grupo de threads (16:29:01,686, conforme o
log do JMeter). São **300 s**, que somam 9.926 amostras.

## 3. Resultados (fase estável, 300 s)

Os tempos estão em ms. O JMeter grava `elapsed` em ms inteiros, então "2" quer dizer "entre 2 e
2,999 ms".

| Operação | n | p50 | p95 | p99 | máx. | Vazão | 2xx | 409 esperado | Erro real | Taxa de erro real |
|---|---|---|---|---|---|---|---|---|---|---|
| `GET /salas/disponiveis` | 7.938 | 1 | **2** | 3 | 13 | 26,46/s (1.587,6/min) | 7.938 | — | 0 | 0,00 % (IC 95 % sup.: 0,038 %) |
| `POST /reservas` (201 + 409) | 1.987 | 4 | **8** | 10 | 14 | 6,62/s (**397,4/min**) | 198 | 1.789 | 0 | 0,00 % (IC 95 % sup.: 0,15 %) |
| ↳ só 201 (venceu) | 198 | 8 | 11 | 12 | 13 | 0,66/s | 198 | — | — | — |
| ↳ só 409 (perdeu) | 1.789 | 3 | 6 | 7 | 14 | 5,96/s | — | 1.789 | — | — |
| `POST /auth/login` | 1 | — | — | — | 2 | — | 1 | — | 0 | (49 dos 50 logins caíram no ramp-up) |
| **Total** | 9.926 | 1 | 5 | 8 | 14 | **33,09/s** | 8.137 | 1.789 | 0 | 0,00 % |

- **"Erro real"** é qualquer status fora de {200} na consulta ou fora de {201, 409} na criação,
  falha de conexão ou falha de asserção. Não houve nenhum: zero 5xx, zero 4xx diferentes de 409 e
  `success=true` em todas as 10.914 amostras.
- **Um 409 só foi classificado como esperado** quando a janela dele teve exatamente um 201. As 1.789
  respostas 409 (1.949 na rodada inteira) atendem a isso, e nenhum 409 terminou antes do 201 da
  própria janela. O `.jtl` não guarda o corpo das respostas, então o motivo do 409 não foi lido
  diretamente. A única outra causa possível de 409, manutenção, está descartada: a única manutenção
  do seed é de 10/03/2026, em outra sala.
- **"IC 95 % sup."** é o limite superior de 95 % para a taxa de erro quando 0 erros são observados
  em *n* amostras (regra do três, 3/*n*). Zero erros nesta amostra não prova que a taxa seja zero.
- **Pouca estatística no p99 do 201:** com n = 198, o p99 depende das 2 amostras mais lentas.

**Rodada inteira, com ramp-up (só para transparência; não é o critério):** consulta n = 8.698, p50
1 / p95 2 / p99 5 / máx. 22 ms; criação n = 2.166, p50 4 / p95 9 / p99 11 / máx. 26 ms
(217 × 201, 1.949 × 409). As amostras mais lentas aparecem nos primeiros 30 s (p99 de 19 ms contra
7 a 9 ms depois), o que é compatível com o aquecimento da JVM. O relatório HTML
(`perf/relatorio-01/`) cobre a rodada inteira e bate com esses números. A vazão por operação que ele
mostra usa o intervalo de cada rótulo (por exemplo, 0,85/s de login em 58 s) e não é comparável à
coluna "Vazão" acima.

**Divergência com o registro anterior em [rnf.md §5](rnf.md):** lá constam 7.906 consultas e 1.977
criações na fase estável. Aqui são 7.938 e 1.987, porque o corte de 60 s foi contado a partir do
início do grupo de threads (registrado no log), e não da primeira amostra (1,26 s depois). Os
percentis não mudam, exceto o máximo da criação: 14 ms nos dois cálculos.

### 3.1 Conferência no banco: no máximo uma reserva aceita por recurso e intervalo

O container da rodada foi removido com `docker rm -f`, mas o volume anônimo do PostgreSQL ficou
(criado às 16:28:26, 35 s antes da rodada). As consultas foram feitas numa **cópia** desse volume,
com o original montado só para leitura. Como o banco não tinha sido desligado de forma limpa, a
cópia passou pela recuperação automática do PostgreSQL. Ela reflete, portanto, as transações
confirmadas.

| Verificação (SQL na cópia) | Resultado |
|---|---|
| Linhas em `reserva` / com `finalidade = 'Teste de carga JMeter'` | 217 / 217, todas `APROVADA` |
| Recurso das linhas de `reserva_recurso` | 217 linhas, todas `SALA 00000000-…-0001`, todas `ativa` |
| Maior número de reservas ativas para o mesmo (recurso, início, fim) | **1** |
| Pares de reservas ativas do mesmo recurso com sobreposição (`periodo && periodo` e também `inicio < fim` em `reserva`) | **0** |
| Janelas distintas no banco / janelas com 201 no `.jtl` | 217 / 217, **o mesmo conjunto** |
| Constraint presente | `ex_reserva_recurso_sem_sobreposicao EXCLUDE USING gist (tipo_recurso =, recurso_id =, periodo &&) WHERE (ativa)` |

O contador do próprio plano, no log do JMeter, diz o mesmo: `RESUMO DISPUTA: 201=217 409=1949
janelas_com_vencedor=217 duplas=0`.

## 4. Comparação com os requisitos e decisão

| Requisito | Critério | Medido (fase estável) | Situação |
|---|---|---|---|
| RNF-01 (oficial) | consulta p95 ≤ 2 s com 50 usuários | 2 ms | Atende |
| RNF-02 (oficial) | escrita p95 ≤ 3 s com a mesma carga | 8 ms | Atende |
| RNF-03 (oficial) | ≥ 100 req/min de criação, 0 % de 5xx | 397,4/min, 0 × 5xx | Atende |
| RNF-01-P (proposta) | p95 ≤ 300 ms; erro < 1 % | 2 ms; 0 % (≤ 0,038 %) | Atende |
| RNF-02-P (proposta) | p95 ≤ 500 ms (201 e 409 juntos); erro < 1 % | 8 ms; 0 % (≤ 0,15 %) | Atende |
| RNF-03-P (proposta) | 0 × 5xx; no máximo um 201 por (sala, janela); ≥ 100 criações/min | 0; máx. 1 (confirmado no banco); 397,4/min | Atende |
| RF-17 (sob carga) | exatamente 1 aceita por recurso/intervalo disputado | 217 janelas com exatamente 1 aceita; 0 duplicatas no banco | Atende |

**Decisão: ATENDE, nas condições desta rodada.** Ela vem com três limitações que o grupo precisa
considerar antes de usar este resultado como evidência:

1. **A versão medida não é reproduzível pelo git:** commit `0cf9518` mais alterações não commitadas
   (seção 1.2).
2. **A meta proposta (300/500 ms) ainda não foi validada pelo grupo** ([rnf.md](rnf.md), "Status:
   PROPOSTA"). Contra os critérios oficiais (2 s / 3 s) a decisão é a mesma.
3. **Ambiente otimista e de uma rodada só.** A margem observada (p95 de 2 ms contra 300 ms) **não**
   pode ser lida como margem em produção: não houve rede, o banco tinha no máximo 217 reservas e o
   gerador de carga dividiu a CPU com a aplicação.

## 5. Hipótese de gargalo

**O que os dados mostram nesta rodada: o sistema não saturou, e a vazão foi limitada pelo próprio
modelo de carga.** Num modelo fechado (N usuários, tempo de pensamento Z, tempo de resposta R), a
lei de Little dá X = N / (Z + R) = 50 / (1,5 s + 0,0015 s) ≈ 33,3 req/s. O medido foi **33,09/s**,
com os blocos de 30 s da fase estável variando de 32,7 a 33,5/s, e o p95 por bloco caiu de 6 para
4 ms ao longo da rodada, em vez de subir. A rodada, portanto, **não identifica um gargalo**; ela só
mostra que, com 50 usuários e 1,5 s de pensamento, nada chegou perto do limite.

**Hipótese para o primeiro gargalo quando a carga subir:** a **seção serializada de criação por
recurso**. `ReservaService.criar` pega um `ReentrantLock` por recurso e, com ele seguro, faz o
`SELECT` de conflito e, se vencer, a transação de `INSERT` em `reserva` e `reserva_recurso` com
commit. Como todos os usuários disputam a mesma sala, todas as criações passam uma a uma por esse
lock.

**Dado que sustenta:**

- O 201 custa p50 8 ms e p99 12 ms; o 409 custa p50 3 ms e p99 7 ms. Os ~5 ms a mais no p50 são a
  transação de escrita com commit (WAL no PostgreSQL dentro de uma VM com 2 vCPUs), feita com o
  lock seguro.
- A consulta, que não passa pelo lock e lê o espelho em memória, fica em p50 1 ms.
- O que **não** sustenta nem derruba a hipótese: sem métricas de CPU, pool e tempo de espera no
  lock (seção 1.3), não dá para separar "espera no lock" de "tempo no banco". Com ~6,6 criações/s e
  alguns ms por criação, a ocupação estimada do lock é de poucos por cento, longe de saturar. A
  hipótese é uma previsão para cargas maiores, não um gargalo observado.

**Previsão que permite refutar:** se a hipótese estiver certa, ao aumentar os usuários o p95 de
`POST /reservas` com sala única sobe antes e mais rápido que o de `GET /salas/disponiveis`, e esse
crescimento some quando a disputa é espalhada por várias salas. Se os dois sobem juntos e
espalhar as salas não muda nada, o gargalo é outro: CPU compartilhada com o gerador de carga, a VM
de 2 vCPUs do banco ou o pool de 10 conexões.

## 6. Próximo experimento (rodada 02)

1. **Pré-requisito:** commitar as três alterações da seção 1.2, gerar o jar a partir de um commit
   limpo e registrar o comando completo da JVM (`-Xms/-Xmx`, JDK). Recriar o banco do zero.
2. **Escada de carga, sala única:** `USUARIOS` = 50, 100, 200 e 400, com o mesmo pensamento,
   `RAMPUP=60` e 5 min estáveis por degrau. Em cada degrau, medir p50, p95 e p99 por operação,
   vazão e taxa de erro, separando 201 e 409, como na seção 3.
3. **Variante de controle:** os mesmos degraus com a disputa espalhada por K salas (por exemplo,
   10), para isolar o efeito do lock por recurso.
4. **Coletar o que faltou nesta rodada:** `docker stats` do banco, CPU e GC da aplicação (`jcmd` ou
   JFR) e métricas do pool Hikari (conexões ativas e threads aguardando).
5. **Repetir o degrau de 50 usuários 3 vezes**, para ter uma noção da variação entre rodadas.
6. **Critério de parada:** o degrau em que o p95 de `POST /reservas` passar de 500 ms, ou a taxa de
   erro real passar de 1 %, ou aparecer algum 5xx, define o ponto de saturação **nesta máquina**.
   Esse ponto também não deve ser tratado como capacidade de produção.
