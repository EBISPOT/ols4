
FROM maven:3.6.3-adoptopenjdk-14

RUN mkdir /opt/ols
COPY . /opt/ols/ 
RUN cd /opt/ols && ls && mvn clean package -DskipTests

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/ols/target/ols4-backend-4.0.0-SNAPSHOT.jar"]


