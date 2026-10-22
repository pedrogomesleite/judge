# Runner C++ do Juizin: worker Java + g++ + isolate (ADR-002, ADR-005, ADR-008).
# O container precisa rodar privilegiado e com cgroup namespace privado (ver compose.yaml).

# 1. Jar do worker
FROM maven:3.9-eclipse-temurin-21 AS jar
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# 2. isolate, compilado da versão fixada
FROM ubuntu:24.04 AS isolate
ARG ISOLATE_VERSION=2.7
RUN apt-get update \
 && apt-get install -y --no-install-recommends build-essential pkg-config libcap-dev libseccomp-dev ca-certificates curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /build
RUN curl -fsSL "https://github.com/ioi/isolate/archive/refs/tags/v${ISOLATE_VERSION}.tar.gz" | tar xz --strip-components=1 \
 && make isolate

# 3. Execução: JRE + g++ (com as bibliotecas estáticas, para o -static) + isolate
FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
 && apt-get install -y --no-install-recommends g++ libc6-dev libcap2 libseccomp2 \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --user-group --no-create-home runner \
 && mkdir -p /var/local/lib/isolate
COPY --from=isolate /build/isolate /usr/local/bin/isolate
RUN chmod 4755 /usr/local/bin/isolate
COPY isolate.cf /usr/local/etc/isolate
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod 755 /usr/local/bin/entrypoint.sh
COPY --from=jar /build/target/runner.jar /app/runner.jar

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
