#!/bin/sh
# Prepara o cgroup v2 do isolate dentro do container privilegiado (ADR-005) e inicia o runner sem root.
# Exige: --privileged e cgroup namespace privado (no compose: privileged: true, cgroup: private).
set -eu

CG=/sys/fs/cgroup

if [ ! -f "$CG/cgroup.controllers" ]; then
  echo "cgroup v2 não encontrado em $CG: o isolate exige cgroup v2 (ADR-005)" >&2
  echo "O host do Docker está em cgroup v1 (docker info --format '{{.CgroupVersion}}'). No WSL2, veja o README do runner-cpp." >&2
  exit 1
fi

# cgroup v2 não aceita processos num grupo que delega controladores aos filhos ("no internal processes"):
# antes de habilitar o controlador de memória, os processos do container passam para um grupo filho.
mkdir -p "$CG/runner"
for pid in $(cat "$CG/cgroup.procs"); do
  echo "$pid" > "$CG/runner/cgroup.procs" 2>/dev/null || true
done
echo "+memory" > "$CG/cgroup.subtree_control"

# Grupo do isolate: ele cria um box-<id> aqui dentro para cada execução, com memory.max próprio.
mkdir -p "$CG/isolate"
echo "+memory" > "$CG/isolate/cgroup.subtree_control"
mkdir -p /run/isolate
echo "$CG/isolate" > /run/isolate/cgroup

# O isolate é setuid root; o worker roda como o usuário runner.
# JAVA_OPTS fica sem aspas de propósito: pode trazer várias opções.
exec setpriv --reuid=runner --regid=runner --init-groups java ${JAVA_OPTS:-} -jar /app/runner.jar
