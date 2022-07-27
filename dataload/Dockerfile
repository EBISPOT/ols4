

# This image, once built, should allow any arbitrary config to be loaded and output neo4j/solr datafiles


FROM maven:3.8-jdk-11



RUN mkdir /opt/neo4j && \
    curl https://dist.neo4j.org/neo4j-community-4.4.9-unix.tar.gz | tar -xz --strip-components=1 -C /opt/neo4j

RUN mkdir /opt/solr && \
    curl https://dlcdn.apache.org/solr/solr/9.0.0/solr-9.0.0.tgz | tar -xz --strip-components=1 -C /opt/solr


RUN mkdir /opt/dataload
COPY . /opt/dataload/ 
RUN cd /opt/dataload && mvn clean package


RUN cp -r /opt/dataload/solr_config/ols4 /opt/solr/server/solr/ols4



RUN mkdir /tmp/out


WORKDIR /opt/dataload

CMD bash -c "\
./create_datafiles.sh file:///mnt/config.json /tmp/out/ontologies.json /tmp/out && \
./load_into_neo4j.sh /opt/neo4j /tmp/out && \
./load_into_solr.sh /opt/solr /tmp/out \
"



