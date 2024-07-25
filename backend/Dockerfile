
#FROM maven:3.6.3-adoptopenjdk-14
FROM maven:3.8-jdk-11

RUN mkdir /opt/ols && mkdir /opt/ols/backend && mkdir /opt/ols/ols-shared
COPY ./backend /opt/ols/backend
COPY ./ols-shared /opt/ols/ols-shared

RUN cd /opt/ols/ols-shared && mvn package

RUN mvn install:install-file -DcreateChecksum=true -Dpackaging=jar -Dfile=/opt/ols/ols-shared/target/ols4-shared-1.0.0-SNAPSHOT.jar -DgroupId=uk.ac.ebi.spot.ols -DartifactId=ols4-shared -Dversion=1.0.0-SNAPSHOT
RUN cd /opt/ols/backend && ls && mvn clean package -DskipTests

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/ols/backend/target/ols4-backend-4.0.0-SNAPSHOT.jar"]


