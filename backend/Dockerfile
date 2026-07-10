
FROM rust:1.90.0-bullseye AS rust-builder

COPY ./text_tagger /opt/text_tagger
RUN cd /opt/text_tagger && cargo build --release

FROM maven:3.9.10-eclipse-temurin-17 AS backend-builder

# Copy text_tagger binary from rust builder
COPY --from=rust-builder /opt/text_tagger/target/release/ols_text_tagger /usr/local/bin/ols_text_tagger
RUN chmod +x /usr/local/bin/ols_text_tagger

RUN mkdir /opt/ols && mkdir /opt/ols/backend && mkdir /opt/ols/ols-shared
COPY ./backend /opt/ols/backend
COPY ./ols-shared /opt/ols/ols-shared

RUN cd /opt/ols/ols-shared && mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn package

RUN mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn install:install-file -DcreateChecksum=true -Dpackaging=jar -Dfile=/opt/ols/ols-shared/target/ols4-shared-1.0.0-SNAPSHOT.jar -DgroupId=uk.ac.ebi.spot.ols -DartifactId=ols4-shared -Dversion=1.0.0-SNAPSHOT
RUN cd /opt/ols/backend && ls && mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn clean package -DskipTests

FROM eclipse-temurin:17-jre

# The backend invokes this binary for text tagging at runtime.
COPY --from=rust-builder /opt/text_tagger/target/release/ols_text_tagger /usr/local/bin/ols_text_tagger
COPY --from=backend-builder /opt/ols/backend/target/ols4-backend-4.0.0-SNAPSHOT.jar /opt/ols/backend/target/ols4-backend-4.0.0-SNAPSHOT.jar

EXPOSE 8080
ENTRYPOINT ["java", "--add-opens=java.base/java.util=ALL-UNNAMED", "-jar", "/opt/ols/backend/target/ols4-backend-4.0.0-SNAPSHOT.jar"]
