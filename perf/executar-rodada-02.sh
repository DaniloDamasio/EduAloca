#!/bin/bash
# Rodada 02 de carga (DOCS/performance/resultado-02.md). Somente localhost.
# Uso: perf/executar-rodada-02.sh .            (rodada completa, ~1 h)
#      SMOKE=1 BASE_OVERRIDE=/tmp/x JM_EXTRA="-JDURACAO=30 -JRAMPUP=5" perf/executar-rodada-02.sh .   (conferencia rapida)
# Requer: Docker, JMeter 5.6.3 no PATH, JDK 21. Brutos em perf/resultados/rodada-02/ (nao versionados).
set -u
cd "$1" || exit 1
# Impede suspensao por ociosidade durante a rodada. Tampa fechada na bateria suspende mesmo assim: por isso cada
# cenario confere o log de energia do macOS e e marcado INVALIDO se houve suspensao (rodada 02, 1a tentativa).
if [ -z "${SOB_CAFFEINATE:-}" ]; then SOB_CAFFEINATE=1 exec caffeinate -dims "$0" "$@"; fi
BASE=${BASE_OVERRIDE:-perf/resultados/rodada-02}
mkdir -p "$BASE"
JAR=target/edualoca-0.0.1-SNAPSHOT.jar
COMMIT=$(git rev-parse --short HEAD)
[ -z "$(git status --porcelain -- src pom.xml perf/reservas.jmx)" ] || { echo "arvore suja; abortando"; exit 1; }
./mvnw -q -B -DskipTests package || exit 1
{ echo "commit=$COMMIT"; echo "jar_sha256=$(shasum -a 256 $JAR | cut -c1-16)"; echo "jmx_sha256=$(shasum -a 256 perf/reservas.jmx | cut -c1-16)";
  echo "csv_sha256=$(shasum -a 256 perf/usuarios.csv | cut -c1-16)"; java -version 2>&1 | head -2; jmeter --version 2>/dev/null | grep -o '5\.[0-9.]*' | head -1;
  docker version --format 'docker={{.Server.Version}}'; sw_vers -productVersion; date '+inicio=%F %T %z'; } > "$BASE/ambiente.txt"

cenario() {
  local nome=$1 usuarios=$2 k=$3 out="$BASE/$1"
  mkdir -p "$out"; echo ">>> $nome usuarios=$usuarios salas=$k $(date '+%T')"
  local inicio_cenario; inicio_cenario=$(date '+%Y-%m-%d %H:%M:%S')
  pmset -g batt | head -1 > "$out/energia.txt"
  docker rm -f edualoca-perf-pg >/dev/null 2>&1
  docker run -d --name edualoca-perf-pg -e POSTGRES_DB=edualoca -e POSTGRES_USER=edualoca \
    -e POSTGRES_PASSWORD=perf-local -p 127.0.0.1:55432:5432 postgres:16-alpine >/dev/null
  until docker exec edualoca-perf-pg pg_isready -U edualoca -d edualoca >/dev/null 2>&1; do sleep 1; done; sleep 2
  local cmd=(java -Xms512m -Xmx512m "-Xlog:gc*:file=$out/gc.log:time,uptime" -jar "$JAR" --spring.profiles.active=perf
    --server.address=127.0.0.1 --edualoca.persistencia.url=jdbc:postgresql://127.0.0.1:55432/edualoca
    --edualoca.persistencia.usuario=edualoca --edualoca.persistencia.senha=perf-local
    --logging.level.com.zaxxer.hikari.pool.HikariPool=DEBUG)
  echo "${cmd[*]}" > "$out/comando-app.txt"
  "${cmd[@]}" > "$out/app.log" 2>&1 & local app=$!
  until curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d '{}' http://127.0.0.1:8080/auth/login; do sleep 1; done
  local extra=()
  if [ "$k" -gt 0 ]; then
    local tok ids=() i
    tok=$(curl -s -X POST -H 'Content-Type: application/json' -d '{"email":"admin@example.com","senha":"senhaAdmin123"}' http://127.0.0.1:8080/auth/login | python3 -I -c 'import sys,json;print(json.load(sys.stdin)["token"])')
    local corpo id
    for i in $(seq 1 "$k"); do
      corpo=$(printf '{"nome":"Sala carga %s","tipo":"aula","capacidade":40,"localizacao":"Bloco A","restrito":false}' "$i")
      id=$(curl -s -X POST -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' -d "$corpo" \
        http://127.0.0.1:8080/salas | python3 -I -c 'import sys,json;print(json.load(sys.stdin)["id"])')
      [ -n "$id" ] || { echo "falha ao criar sala $i"; kill $app; exit 1; }
      ids+=("$id")
    done
    local lista; lista=$(IFS=,; echo "${ids[*]}"); echo "$lista" > "$out/salas.txt"; extra=(-JSALA_IDS="$lista")
  fi
  ( while kill -0 $app 2>/dev/null; do
      echo "$(date +%s) app $(ps -o %cpu=,rss= -p $app | xargs) pg $(docker stats --no-stream --format '{{.CPUPerc}} {{.MemUsage}}' edualoca-perf-pg)"
      sleep 5; done ) > "$out/recursos.txt" 2>/dev/null & local amostra=$!
  jmeter -n -t perf/reservas.jmx -JUSUARIOS="$usuarios" ${JM_EXTRA:-} ${extra[@]+"${extra[@]}"} -l "$out/rodada.jtl" -j "$out/jmeter.log" > "$out/jmeter-stdout.txt" 2>&1
  docker exec edualoca-perf-pg psql -U edualoca -d edualoca -At -F ' ' -c "
    SELECT 'reservas', count(*) FROM reserva
    UNION ALL SELECT 'linhas_ativas', count(*) FROM reserva_recurso WHERE ativa
    UNION ALL SELECT 'max_por_recurso_periodo', coalesce(max(c),0) FROM (SELECT count(*) c FROM reserva_recurso WHERE ativa GROUP BY recurso_id, periodo) x
    UNION ALL SELECT 'pares_sobrepostos', count(*) FROM reserva_recurso a JOIN reserva_recurso b
      ON a.recurso_id = b.recurso_id AND a.id < b.id AND a.periodo && b.periodo WHERE a.ativa AND b.ativa
    UNION ALL SELECT 'recursos_distintos', count(DISTINCT recurso_id) FROM reserva_recurso;" > "$out/banco.txt" 2>&1
  kill $app; wait $app 2>/dev/null; kill $amostra 2>/dev/null
  grep -h "RESUMO DISPUTA" "$out/jmeter.log" | tail -1 > "$out/resumo-disputa.txt"
  local fim_cenario; fim_cenario=$(date '+%Y-%m-%d %H:%M:%S')
  pmset -g log | awk -v a="$inicio_cenario" -v b="$fim_cenario" 'substr($0,1,19) >= a && substr($0,1,19) <= b && /Entering Sleep/' > "$out/suspensoes.txt"
  if [ -s "$out/suspensoes.txt" ]; then echo "INVALIDO: a maquina suspendeu durante o cenario" > "$out/INVALIDO"; fi
  echo "<<< $nome fim $(date '+%T') $(cat "$out/resumo-disputa.txt" | sed 's/.*RESUMO/RESUMO/') $( [ -f "$out/INVALIDO" ] && echo '[INVALIDO: suspensao]')"
}

if [ -n "${SMOKE:-}" ]; then cenario smoke 5 0; cenario smoke-k3 5 3; else
cenario 50-a 50 0
cenario 50-b 50 0
cenario 50-c 50 0
cenario 100 100 0
cenario 200 200 0
cenario 400 400 0
cenario 200-k10 200 10
cenario 400-k10 400 10
fi
docker rm -f edualoca-perf-pg >/dev/null 2>&1
date '+fim=%F %T %z' >> "$BASE/ambiente.txt"
echo FIM
