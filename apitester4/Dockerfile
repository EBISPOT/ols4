
FROM maven:3.8-jdk-11

ENV JAVA_TOOL_OPTIONS="--add-opens=java.base/java.lang=ALL-UNNAMED"

ADD . /opt/apitester

RUN cd /opt/apitester && mvn clean package


