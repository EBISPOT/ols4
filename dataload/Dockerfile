

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
RUN mkdir /opt/dataload
COPY . /opt/dataload/ 
RUN cd /opt/dataload && mvn clean package

# Copy the OLS4 Solr config into our temporary Solr server
#
RUN rm -rf /opt/solr/server/solr
RUN cp -r /opt/dataload/solr_config /opt/solr/server/solr

RUN mkdir /tmp/out

RUN chown -R ols:ols /opt/* /tmp/out

USER ols
WORKDIR /opt/dataload

CMD ./dataload.dockersh


