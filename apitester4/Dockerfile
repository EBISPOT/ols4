
FROM maven:3-eclipse-temurin-17 AS builder

ADD . /opt/apitester

RUN cd /opt/apitester && mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn clean package

FROM eclipse-temurin:17-jre

COPY --from=builder /opt/apitester/target/apitester-1.0-SNAPSHOT-jar-with-dependencies.jar /opt/apitester/apitester.jar

WORKDIR /opt/apitester
