# Resultado da rodada 02: escada de carga, variante de controle e repetições

**Resultado vigente** do teste de carga. A [rodada 01](resultado-01.md) fica como histórico: ela mediu uma versão
anterior, que não corresponde a nenhum commit.

- **Data:** 2026-11-12: rodada principal das 18:07:28 às 18:56:37 e repetição na tomada das 19:01:11 às 19:19:36 (UTC−3).
- **Plano:** [rnf.md](rnf.md) (requisitos e modelo de carga), [perf/reservas.jmx](../../perf/reservas.jmx) e o script
  versionado [perf/executar-rodada-02.sh](../../perf/executar-rodada-02.sh). O script recria banco e aplicação em
  cada cenário, amostra CPU e memória a cada 5 s, liga o log de GC e as estatísticas do pool Hikari, confere dupla
  reserva por SQL e marca o cenário como inválido se o macOS suspender.
- **Brutos:** `perf/resultados/rodada-02/<cenário>/` (`rodada.jtl`, `jmeter.log`, `app.log`, `gc.log`, `recursos.txt`,
  `banco.txt`, `energia.txt`, `suspensoes.txt`). Pela regra do plano, os brutos não são versionados.
- **Análise:** números recalculados a partir do `.jtl` (percentil *nearest-rank* sobre `elapsed` em ms inteiros), na
  **fase estável** de 60 s a 360 s após a primeira amostra (300 s), como na rodada 01.

> **Escopo desta conclusão.** Vale **para esta máquina, esta versão e esta carga**. Gerador de carga, aplicação e
> banco rodaram no mesmo notebook, sem rede entre eles e com o banco começando vazio a cada cenário. Estes números não
> permitem afirmar nada sobre produção.

## 0. O que esta rodada corrige em relação à rodada 01

| Limite da rodada 01 | Nesta rodada |
|---|---|
| Versão medida = `0cf9518` + alterações não commitadas | Commit limpo `38bbc04`; jar `d66c57130a04e87f` registrado e guardado |
| Comando da JVM não registrado | `comando-app.txt` em cada cenário: `-Xms512m -Xmx512m`, perfil `perf`, PostgreSQL |
| Sem CPU, memória, GC nem pool | Coletados (seção 4) |
| Uma rodada só | 3 repetições de 50 usuários na bateria e 3 na tomada (seção 5) |
| Gargalo só como hipótese | Escada 50 → 100 → 200 → 400 e variante com a disputa espalhada por 10 salas |

**Primeira tentativa descartada.** A primeira execução (16:24–18:03) se perdeu: às 16:42:03 o notebook entrou em
suspensão por tampa fechada ("Clamshell Sleep", na bateria, conforme `pmset -g log`). O cenário de 100 usuários levou
19 min em vez de 6, e os de 200 e 400 registraram 7 amostras em 15 min. Só 50-a e 50-b daquela tentativa ficaram
íntegros. Nada daquela execução entra nas tabelas abaixo. O script passou a rodar sob `caffeinate` e a marcar o
cenário como inválido se houver suspensão no intervalo. **Nenhum cenário desta rodada foi marcado.**

## 1. Ambiente

| Item | Valor |
|---|---|
| Equipamento | Apple M4 Pro, 12 núcleos, 24 GiB de RAM; macOS 26.7.2 |
| Docker | Engine 29.5.2 (Colima), VM com **2 vCPUs e 3,8 GiB**, onde roda o PostgreSQL |
| PostgreSQL | 16 (`postgres:16-alpine`), container recriado a cada cenário, porta 55432 no loopback |
| Aplicação | `edualoca-0.0.1-SNAPSHOT.jar` do commit `38bbc04` (SHA-256 `d66c57130a04e87f`), JDK Temurin 21.0.9, `-Xms512m -Xmx512m`, perfil `perf`, `ReservaRepositorioJdbc`, pool Hikari de 10 conexões |
| JMeter | 5.6.3, modo CLI, na mesma máquina; plano `cdf79be50df25847`; CSV `a4b336ba2b8cb3d9` (o mesmo da rodada 01) |
| Energia | 50-a e 50-b na **bateria** (modo de energia "Automático"). 50-c começou às 18:19:49 na bateria e o carregador foi ligado em seguida, então teve energia mista. 100 em diante na **tomada** (modo "Alto desempenho"). Por isso as três repetições foram refeitas na tomada (seção 5) |
| Outros processos | Não controlados (notebook de uso pessoal). **Durante o 50-b, outro trabalho em andamento no mesmo diretório rodou um `./mvnw -B verify` (cerca de 40 s, relato do próprio trabalho).** Nos demais cenários não houve build nem teste em paralelo |

## 2. Carga aplicada

Mesmo modelo da rodada 01 ([rnf.md §3](rnf.md)): pensamento uniforme de 1 a 2 s, ramp-up de 60 s, 360 s por cenário,
cerca de 80% consultas e 20% criações, e cerca de 10 tentativas de criação por janela de 1 h. As contas do CSV (50)
são reaproveitadas pelas threads acima de 50, e cada thread faz o próprio login.

| Cenário | Usuários | Disputa |
|---|---|---|
| 50-a, 50-b, 50-c | 50 | 1 sala (repetições) |
| 100, 200, 400 | 100, 200, 400 | 1 sala (escada) |
| 200-k10, 400-k10 | 200, 400 | 10 salas: a janela *n* disputa a sala *n* mod 10 (cada janela continua com uma sala só) |

## 3. Resultados (fase estável, 300 s)

Tempos em ms (`elapsed` inteiro: "2" quer dizer entre 2 e 2,999 ms). "Erro real" é qualquer status fora de 200 na
consulta, ou fora de 201/409 na criação. 409 é o resultado correto de quem perde a disputa.

| Cenário | Usuários | Consultas | p50 | p95 | p99 | máx. | Consultas/s | Criações | p50 | p95 | p99 | máx. | Criações/min | 201: n / p95 | 409: n / p95 | Total/s | Erro real | 5xx |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 50-a | 50 | 7967 | 1 | 2 | 3 | 15 | 26,56 | 1988 | 4 | 11 | 14 | 26 | 397,6 | 198 / 15 | 1790 / 6 | 33,18 | 0 | 0 |
| 50-b | 50 | 7952 | 1 | 2 | 3 | 7 | 26,51 | 1988 | 4 | 11 | 14 | 23 | 397,6 | 199 / 15 | 1789 / 6 | 33,13 | 0 | 0 |
| 50-c | 50 | 7945 | 1 | 2 | 3 | 14 | 26,48 | 1987 | 5 | 12 | 15 | 17 | 397,4 | 199 / 15 | 1788 / 6 | 33,11 | 0 | 0 |
| 100 | 100 | 15866 | 1 | 2 | 2 | 9 | 52,89 | 3962 | 4 | 11 | 13 | 19 | 792,4 | 395 / 14 | 3567 / 6 | 66,09 | 0 | 0 |
| 200 | 200 | 31736 | 1 | 1 | 2 | 12 | 105,79 | 7933 | 3 | 10 | 13 | 25 | 1586,6 | 794 / 13 | 7139 / 5 | 132,23 | 0 | 0 |
| 400 | 400 | 63478 | 1 | 2 | 2 | 10 | 211,59 | 15871 | 3 | 9 | 12 | 24 | 3174,2 | 1588 / 13 | 14283 / 5 | 264,50 | 0 | 0 |
| 200-k10 | 200 | 31816 | 1 | 3 | 4 | 11 | 106,05 | 7947 | 3 | 9 | 11 | 19 | 1589,4 | 795 / 12 | 7152 / 4 | 132,55 | 0 | 0 |
| 400-k10 | 400 | 63394 | 1 | 3 | 4 | 10 | 211,31 | 15847 | 2 | 8 | 10 | 20 | 3169,4 | 1584 / 11 | 14263 / 3 | 264,14 | 0 | 0 |

### 3.1 Correção sob disputa (RF-17)

Contagem do próprio plano (`RESUMO DISPUTA`, rodada inteira) e conferência no banco ao fim de cada cenário:

| Cenário | 201 | 409 | Janelas com vencedor | Duplas (plano) | Reservas no banco | Máx. por (recurso, período) | Pares sobrepostos | Recursos distintos |
|---|---|---|---|---|---|---|---|---|
| 50-a | 218 | 1958 | 218 | 0 | 218 | 1 | 0 | 1 |
| 50-b | 218 | 1959 | 218 | 0 | 218 | 1 | 0 | 1 |
| 50-c | 218 | 1958 | 218 | 0 | 218 | 1 | 0 | 1 |
| 100 | 434 | 3905 | 434 | 0 | 434 | 1 | 0 | 1 |
| 200 | 871 | 7814 | 871 | 0 | 871 | 1 | 0 | 1 |
| 400 | 1740 | 15626 | 1740 | 0 | 1740 | 1 | 0 | 1 |
| 200-k10 | 871 | 7821 | 871 | 0 | 871 | 1 | 0 | 10 |
| 400-k10 | 1738 | 15614 | 1738 | 0 | 1738 | 1 | 0 | 10 |

Em **todos** os cenários: exatamente uma reserva aceita por janela disputada, **0 duplicatas** no banco (consulta de
pares com o mesmo recurso e períodos sobrepostos, só entre linhas ativas) e 0 erros reais.

## 4. Recursos durante a fase estável

CPU pelo `ps` (100% = 1 núcleo; a máquina tem 12). Memória = RSS da JVM. CPU do banco pelo `docker stats` (100% = 1
das 2 vCPUs da VM). GC = pausas registradas em `gc.log`. Hikari = estatísticas do *housekeeper*, uma amostra a cada
30 s (10 por cenário).

| Cenário | CPU app média / máx. (%) | RSS máx. (MiB) | CPU banco média / máx. (%) | GC: pausas / total (ms) / máx. (ms) | Hikari: ativas máx. / aguardando máx. |
|---|---|---|---|---|---|
| 50-a | 4,9 / 29,7 | 526 | 0,5 / 1,6 | 21 / 80,0 / 12,6 | 1 / 0 |
| 50-b | 3,3 / 14,0 | 522 | 0,5 / 1,3 | 21 / 74,9 / 13,7 | 1 / 0 |
| 50-c | 4,0 / 10,3 | 522 | 0,6 / 2,7 | 21 / 74,8 / 12,6 | 0 / 0 |
| 100 | 5,2 / 21,3 | 534 | 1,0 / 3,4 | 28 / 108,1 / 8,8 | 0 / 0 |
| 200 | 8,3 / 29,9 | 541 | 2,1 / 4,3 | 50 / 144,7 / 5,8 | 1 / 0 |
| 400 | 15,9 / 38,4 | 560 | 3,3 / 7,6 | 111 / 291,4 / 6,0 | 1 / 0 |
| 200-k10 | 14,8 / 27,7 | 556 | 1,6 / 4,5 | 113 / 273,0 / 5,9 | 1 / 0 |
| 400-k10 | 29,8 / 47,5 | 589 | 2,6 / 6,0 | 355 / 651,4 / 9,6 | 0 / 0 |

## 5. Repetibilidade e comparação com a rodada 01

As seis execuções de 50 usuários (três na bateria e três na tomada) e a rodada 01, na fase estável:

| Execução | Energia | Consultas | p50 | p95 | p99 | máx. | Criações | p50 | p95 | p99 | máx. | Criações/min | 201 p95 | 409 p95 | Total/s | Erro real | CPU app média (%) | Maior pausa de GC (ms) | Reservas / pares sobrepostos |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 50-a | bateria | 7967 | 1 | 2 | 3 | 15 | 1988 | 4 | 11 | 14 | 26 | 397,6 | 15 | 6 | 33,18 | 0 | 4,9 | 12,6 | 218 / 0 |
| 50-b | bateria | 7952 | 1 | 2 | 3 | 7 | 1988 | 4 | 11 | 14 | 23 | 397,6 | 15 | 6 | 33,13 | 0 | 3,3 | 13,7 | 218 / 0 |
| 50-c | mista | 7945 | 1 | 2 | 3 | 14 | 1987 | 5 | 12 | 15 | 17 | 397,4 | 15 | 6 | 33,11 | 0 | 4,0 | 12,6 | 218 / 0 |
| 50-a-tomada | tomada | 7921 | 1 | 2 | 3 | 14 | 1976 | 4 | 12 | 14 | 20 | 395,2 | 14 | 6 | 32,99 | 0 | 4,5 | 13,5 | 218 / 0 |
| 50-b-tomada | tomada | 7955 | 1 | 2 | 3 | 9 | 1987 | 4 | 12 | 14 | 22 | 397,4 | 15 | 7 | 33,14 | 0 | 4,3 | 5,7 | 219 / 0 |
| 50-c-tomada | tomada | 7929 | 1 | 2 | 3 | 15 | 1982 | 4 | 11 | 14 | 20 | 396,4 | 15 | 7 | 33,04 | 0 | 4,2 | 12,9 | 218 / 0 |
| Rodada 01 | não registrada | 7938 | 1 | 2 | 3 | 13 | 1987 | 4 | 8 | 10 | 14 | 397,4 | 11 | 6 | 33,09 | 0 | não medida | não medida | 217 / 0 |

As repetições de 50 usuários na tomada (`50-a-tomada` a `50-c-tomada`) usaram **o mesmo jar e o mesmo plano** da rodada
principal (SHA-256 `d66c57130a04e87f` e `cdf79be50df25847`), sem recompilar. Isso permitiu repeti-las mesmo com
alterações de outro trabalho em andamento no diretório.

**Interpretação.**

- **50-b é suspeito.** Durante ele houve um build em paralelo (seção 1). Nos números não aparece efeito: a fase
  estável do 50-b é indistinguível da do 50-a (mesmos percentis e vazão, 14,0% de CPU máxima na fase estável), e o
  pico de 368,8% de CPU registrado às 18:13:41 é a primeira amostra, durante a subida da aplicação e antes da primeira
  requisição. Ainda assim, por precaução, **a base limpa para a repetibilidade são as três execuções na tomada**, sem
  interferência.
- **Repetibilidade alta.** Entre as seis execuções, o p95 de consulta foi sempre 2 ms, o de criação variou entre 11 e
  12 ms, a vazão de criação entre 395,2 e 397,6/min, e as reservas aceitas entre 218 e 219. A dispersão está dentro da
  resolução da medida (ms inteiros).
- **Bateria × tomada não fez diferença mensurável** nessa carga: as médias dos dois grupos diferem em menos de 1 ms
  em todos os percentis. A troca de modo de energia ("Automático" → "Alto desempenho") não afeta a conclusão.
- **Em relação à rodada 01:** consulta e vazão iguais (p95 de 2 ms, ~397/min). A **criação ficou de 3 a 4 ms mais
  lenta no p95** (8 → 11–12 ms), quase toda nos 201 (11 → 14–15 ms), com os 409 iguais (6–7 ms). **Hipótese, não
  verificada:** entre as duas versões, a criação aceita passou a gravar o registro de histórico (`reserva_historico`) na
  mesma transação, a auditoria passou a registrar cada recusa por sobreposição, e a notificação passou a ser disparada
  nas transições. Um `INSERT` a mais por 201, com commit numa VM de 2 vCPUs, é compatível com a diferença. Para
  confirmar, seria preciso medir o tempo de cada etapa (JFR) ou comparar as duas versões na mesma máquina e no mesmo
  dia. A rodada 01 não pode ser reproduzida, porque mediu alterações não commitadas.
- Os valores continuam **duas ordens de grandeza abaixo** das metas (2 s e 3 s oficiais; 300 e 500 ms propostas).

## 6. Comparação com os requisitos e decisão

| Requisito | Critério | Pior caso medido (fase estável) | Situação |
|---|---|---|---|
| RNF-01 (oficial) | consulta p95 ≤ 2 s com 50 usuários | 2 ms (50 usuários); 3 ms em qualquer cenário até 400 | Atende |
| RNF-02 (oficial) | escrita p95 ≤ 3 s com a mesma carga | 12 ms (50 usuários); 15 ms só para os 201 | Atende |
| RNF-03 (oficial) | ≥ 100 criações/min e 0% de 5xx | 397,4/min com 50 usuários; 0 × 5xx em todos os cenários | Atende |
| RNF-01-P (proposta) | p95 ≤ 300 ms; erro < 1% | 3 ms; 0 erros em até 63.478 consultas | Atende |
| RNF-02-P (proposta) | p95 ≤ 500 ms (201 e 409 juntos); erro < 1% | 12 ms; 0 erros em até 15.871 criações | Atende |
| RNF-03-P (proposta) | 0 × 5xx; no máximo um 201 por (sala, janela); ≥ 100 criações/min | 0; máx. 1 confirmado no banco; mín. 397,4/min | Atende |
| RF-17 sob carga | exatamente 1 aceita por recurso/intervalo disputado | 6.308 janelas com exatamente 1 aceita (soma dos 8 cenários); 0 duplicatas | Atende |

**Decisão: ATENDE, com margem, em todos os cenários até 400 usuários, nesta máquina.** O critério de parada do
plano (p95 de criação > 500 ms, erro real > 1% ou qualquer 5xx) **não foi atingido**, então a rodada não encontrou o
ponto de saturação.

## 7. Hipótese de gargalo, revisitada

**O que a rodada 01 previu:** com mais usuários, o p95 de `POST /reservas` com sala única subiria antes e mais rápido
que o de `GET /salas/disponiveis`, porque todas as criações passam pelo `ReentrantLock` do recurso disputado; e esse
crescimento sumiria ao espalhar a disputa por várias salas.

**O que esta rodada mostra:**

1. **O sistema não saturou até 400 usuários.** A vazão cresceu linearmente com os usuários (33 → 66 → 132 → 264
   req/s) e bate com o que a lei de Little prevê para o modelo fechado: X = N / (Z + R) ≈ 400 / 1,5 s ≈ 266,6 req/s
   contra 264,5 medidos. A vazão continua limitada pelo **tempo de pensamento do modelo de carga**, não pelo sistema.
2. **A previsão não se confirmou nessa faixa.** O p95 de criação com sala única **não subiu**: foi de 11–12 ms (50)
   para 9 ms (400). O de consulta ficou em 1–2 ms. Espalhar a disputa por 10 salas baixou o p95 de criação em 1 ms
   (9 → 8 ms com 400 usuários; 409 de 5 → 3 ms). A diferença existe, mas está no nível do ruído de uma medição em ms
   inteiros.
3. **Nenhum recurso chegou perto do limite:** a aplicação usou em média 16% de um núcleo com 400 usuários (máx. 38%),
   o banco 3,3% de uma vCPU, o pool Hikari teve no máximo 1 conexão ativa das 10 e nenhuma thread aguardando, e a maior
   pausa de GC foi de 13,7 ms.
4. **A variante de controle tem uma variável a mais.** Com 10 salas cadastradas a mais, cada resposta de
   `GET /salas/disponiveis` ficou 2,9 vezes maior (média de 2.143 bytes contra 751). Isso explica por que os cenários
   k10 gastaram cerca de 2 vezes mais CPU e tiveram cerca de 3 vezes mais pausas de GC, e por que o p95 de consulta
   subiu de 2 para 3 ms. **Não é efeito do lock.** Uma comparação limpa precisa manter o mesmo número de salas
   cadastradas nos dois braços.

**Conclusão:** o lock por recurso continua sendo o candidato a primeiro gargalo por construção (serializa as
criações de um mesmo recurso), mas **não há dado que o mostre como gargalo até 400 usuários e ~53 criações/s**. A
ocupação do lock ainda é baixa: cerca de 53 criações/s × 3–4 ms dá ~20% de ocupação, no máximo.

## 8. Limites desta rodada

- Gerador de carga e aplicação na mesma máquina. A CPU do próprio JMeter **não foi medida**, então não dá para separar
  "folga da aplicação" de "folga da máquina". Com 400 threads, o JMeter pode ter sido o maior consumidor de CPU.
- Pool Hikari amostrado a cada 30 s: picos curtos de conexões ativas entre amostras não aparecem.
- Banco vazio a cada cenário (até 1.740 reservas no fim): consultas e constraint não foram medidas com um volume de
  dados realista.
- Sem rede entre os componentes: a latência real de rede não está nos números.
- A notificação está no modo simulado (padrão). Com a integração externa habilitada, cada transição faria um `POST`
  HTTP para fora, o que não foi medido.
- Esta versão (`38bbc04`) não tem o agendador de não comparecimento automático que está sendo desenvolvido em
  paralelo. Se ele entrar, a carga deve ser medida de novo.

## 9. Próximo experimento (rodada 03)

1. **Tirar o limite do modelo de carga:** reduzir o pensamento (`-JPENSAR_MS=0 -JPENSAR_VAR_MS=100`) ou passar a um
   modelo aberto (vazão-alvo constante) e subir até o critério de parada. É o único jeito de achar a saturação.
2. **Gerador em outra máquina**, ou pelo menos medir a CPU do JMeter, para que o gerador não seja o gargalo invisível.
3. **Controle limpo:** as mesmas 10 salas cadastradas nos dois braços, mudando só o espalhamento da disputa.
4. **Métricas contínuas do pool e do lock:** expor o Hikari via JMX/Actuator e medir o tempo de espera no
   `ReentrantLock` (JFR), em vez de amostras a cada 30 s.
5. **Banco com volume:** semear dezenas de milhares de reservas antes de medir.

## Onde estão os dados (atualização de 2026-11-12, ~19:55)

A regra "os brutos não são versionados" mudou: os **resumos e logs de cada rodada passaram a ser versionados** em
`perf/resultados/`, para que cada número deste documento aponte para um arquivo do repositório. Só os `.jtl` (uma linha
por requisição) ficam fora do git, por tamanho. O autor guarda os arquivos compactados abaixo, e os SHA-256 permitem
conferir uma cópia, se for pedida:

| Arquivo | Conteúdo | SHA-256 |
|---|---|---|
| `rodada-02-jtl.tar.gz` (2,9 MB) | os 11 `rodada.jtl` da rodada 02 (8 cenários e 3 repetições), com o caminho `rodada-02/<cenário>/` | `99cf7ff406e36877f86f38646dd85def550e385f4c81f014f08e056077ca42cf` |
| `rodada-01.jtl.gz` (81 KB) | `rodada-01.jtl`; descompactado, o SHA-256 começa com `dcfeada7c825bdee`, igual ao da rodada 01 | `78bc783f22efdf20e49ca89bc04130c566169245e1d32498fee6298fe11cbbe0` |

Para conferir uma cópia: `shasum -a 256 <arquivo>` e extrair em `perf/resultados/`
(`tar -xzf rodada-02-jtl.tar.gz -C perf/resultados`). A tentativa descartada da rodada 02 (suspensão da máquina) e o
relatório HTML do JMeter (regerável a partir do `.jtl`) não são guardados.
