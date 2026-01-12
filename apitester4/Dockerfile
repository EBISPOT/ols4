
FROM maven:3.8-jdk-11 AS builder

ADD . /opt/apitester

RUN cd /opt/apitester && mvn clean package

FROM eclipse-temurin:11-jre

COPY --from=builder /opt/apitester/target/apitester-1.0-SNAPSHOT-jar-with-dependencies.jar /opt/apitester/apitester.jar

WORKDIR /opt/apitester
