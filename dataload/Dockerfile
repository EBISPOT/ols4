
FROM rust:1.90.0-bullseye AS rust-builder

COPY ./dataload /opt/ols/dataload
COPY ./text_tagger /opt/ols/text_tagger

RUN cd /opt/ols/dataload && cargo build --release
RUN cd /opt/ols/text_tagger && cargo build --release

FROM maven:3.9.9-eclipse-temurin-21 AS maven-builder

RUN mkdir /opt/ols && mkdir /opt/ols/dataload && mkdir /opt/ols/ols-shared

COPY ./dataload /opt/ols/dataload
COPY ./ols-shared /opt/ols/ols-shared
COPY ./pom.xml /opt/ols

RUN cd /opt/ols/ols-shared && mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn package

RUN mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn install:install-file -DcreateChecksum=true -Dpackaging=jar -Dfile=/opt/ols/ols-shared/target/ols4-shared-1.0.0-SNAPSHOT.jar -DgroupId=uk.ac.ebi.spot.ols -DartifactId=ols4-shared -Dversion=1.0.0-SNAPSHOT
RUN cd /opt/ols/dataload && mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -T 1C package

FROM eclipse-temurin:21-jre-noble

# PostgreSQL 17 + pgvector: the Nextflow pipeline runs a local PG cluster inside
# this image (load_into_postgres.py) unless --external_postgres is set.
RUN apt-get update -y && apt-get install -y --no-install-recommends \
        gnupg lsb-release ca-certificates curl pigz python3 python3-pip libnss-wrapper && \
    install -d /etc/apt/keyrings && \
    curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /etc/apt/keyrings/postgresql.gpg && \
    echo "deb [signed-by=/etc/apt/keyrings/postgresql.gpg] http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list && \
    apt-get update -y && \
    apt-get install -y --no-install-recommends postgresql-17 postgresql-17-pgvector && \
    apt-get purge -y --auto-remove gnupg lsb-release && \
    rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir --break-system-packages pyarrow

RUN addgroup --system ols && adduser --system --ingroup ols ols

RUN mkdir -p /opt/ols/dataload/merge_configs/target \
    /opt/ols/dataload/rdf2json/target \
    /opt/ols/dataload/reporting/target \
    /opt/ols/dataload/extras/json2sssom/target

# Java CLIs invoked by the Nextflow pipeline (paths matched to ols_dataload.nf)
COPY --from=maven-builder /opt/ols/dataload/merge_configs/target/merge_configs-1.0-SNAPSHOT.jar /opt/ols/dataload/merge_configs/target/
COPY --from=maven-builder /opt/ols/dataload/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar /opt/ols/dataload/rdf2json/target/
COPY --from=maven-builder /opt/ols/dataload/reporting/target/reporting-1.0-SNAPSHOT.jar /opt/ols/dataload/reporting/target/
COPY --from=maven-builder /opt/ols/dataload/extras/json2sssom/target/json2sssom-1.0-SNAPSHOT.jar /opt/ols/dataload/extras/json2sssom/target/

# Rust binaries invoked by the Nextflow pipeline
COPY --from=rust-builder /opt/ols/dataload/target/release/ols_json2postgres /usr/local/bin/
COPY --from=rust-builder /opt/ols/dataload/target/release/ols_create_manifest /usr/local/bin/
COPY --from=rust-builder /opt/ols/dataload/target/release/ols_link /usr/local/bin/
COPY --from=rust-builder /opt/ols/dataload/target/release/extract_strings_from_terms /usr/local/bin/
COPY --from=rust-builder /opt/ols/text_tagger/target/release/ols_text_tagger /usr/local/bin/
RUN chmod +x /usr/local/bin/ols_json2postgres /usr/local/bin/ols_create_manifest /usr/local/bin/ols_link /usr/local/bin/extract_strings_from_terms /usr/local/bin/ols_text_tagger

# Python scripts invoked by the Nextflow pipeline (local + external PG paths)
COPY ./dataload/load_into_postgres.py ./dataload/populate_external_postgres.py ./dataload/create_postgres_schema.py /opt/ols/dataload/

RUN mkdir /tmp/out

RUN chown -R ols:ols /opt/ols /tmp/out
RUN chmod -R 777 /opt/ols /tmp/out

USER ols
WORKDIR /opt/ols/dataload

CMD ./dataload.dockersh
