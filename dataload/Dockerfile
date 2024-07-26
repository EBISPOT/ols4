

# This image, once built, should allow any arbitrary config to be loaded and output neo4j/solr datafiles


FROM maven:3.8-jdk-11

RUN addgroup --system --gid 1000 ols && adduser --system --uid 1000 --ingroup ols ols

# Extract Neo4j and Solr vanilla installs to /opt/neo4j and /opt/solr
#
# We use these only as temporary servers for the dataload. The "real" Neo4j 
# and Solr servers are the standard images specified in docker-compose.yml
#
RUN mkdir /opt/neo4j && \
    curl https://dist.neo4j.org/neo4j-community-4.4.9-unix.tar.gz | tar -xz --strip-components=1 -C /opt/neo4j

RUN mkdir /opt/solr && \
    curl https://archive.apache.org/dist/solr/solr/9.0.0/solr-9.0.0.tgz | tar -xz --strip-components=1 -C /opt/solr

RUN echo "dbms.security.auth_enabled=false" >> /opt/neo4j/conf/neo4j.conf
RUN echo "dbms.jvm.additional=-Dorg.neo4j.kernel.impl.index.schema.GenericNativeIndexPopulator.blockBasedPopulation=true" >> /opt/neo4j/conf/neo4j.conf

# Copy all the code for dataload into /opt/dataload and build the JARs
#

RUN mkdir /opt/ols && mkdir /opt/ols/dataload && mkdir /opt/ols/ols-shared


COPY ./dataload /opt/ols/dataload
COPY ./ols-shared /opt/ols/ols-shared
COPY ./pom.xml /opt/ols

RUN cd /opt/ols/ols-shared && mvn package

RUN mvn install:install-file -DcreateChecksum=true -Dpackaging=jar -Dfile=/opt/ols/ols-shared/target/ols4-shared-1.0.0-SNAPSHOT.jar -DgroupId=uk.ac.ebi.spot.ols -DartifactId=ols4-shared -Dversion=1.0.0-SNAPSHOT
RUN cd /opt/ols/dataload &&  mvn package

# Copy the OLS4 Solr config into our temporary Solr server
#
RUN rm -rf /opt/solr/server/solr
RUN cp -r /opt/ols/dataload/solr_config /opt/solr/server/solr

RUN mkdir /tmp/out

RUN chown -R ols:ols /opt/* /tmp/out
RUN chmod -R 777 /opt/* /tmp/out

USER ols
WORKDIR /opt/ols/dataload

CMD ./dataload.dockersh


