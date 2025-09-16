
# This image, once built, should allow any arbitrary config to be loaded and output neo4j/solr datafiles

FROM rust:1.79-buster

SHELL ["/bin/bash", "-c"]

RUN sed -i 's|http://deb.debian.org/debian|http://archive.debian.org/debian|g' /etc/apt/sources.list && \
    sed -i 's|http://deb.debian.org/debian-security|http://archive.debian.org/debian-security|g' /etc/apt/sources.list && \
    apt-get update

RUN addgroup --system --gid 1000 ols && adduser --system --uid 1000 --ingroup ols ols

# Install JDK
#
RUN arch=$(uname -m); arch=${arch/x86_64/x64}; \
        curl https://corretto.aws/downloads/resources/21.0.6.7.1/amazon-corretto-21.0.6.7.1-linux-$arch.tar.gz | tar -C /opt -xzf - && \
        mv /opt/amazon-corretto* /opt/amazon-corretto && \
        echo 'export JAVA_HOME=/opt/amazon-corretto' >> ~/.bashrc && \
        echo 'export PATH=$PATH:$JAVA_HOME/bin' >> ~/.bashrc
ENV JAVA_HOME="/opt/amazon-corretto/"
ENV PATH="$PATH:/opt/amazon-corretto/bin"

# Install Maven
#
RUN curl https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz | tar -C /usr/local --strip-components=1 -xzf -

# Extract Neo4j and Solr vanilla installs to /opt/neo4j and /opt/solr
#
# We use these only as temporary servers for the dataload. The "real" Neo4j 
# and Solr servers are the standard images specified in docker-compose.yml
# It is important that the versions match!
#
RUN mkdir /opt/neo4j && \
    curl https://dist.neo4j.org/neo4j-community-2025.03.0-unix.tar.gz | tar -xz --strip-components=1 -C /opt/neo4j

RUN mkdir /opt/solr && \
    curl https://archive.apache.org/dist/solr/solr/9.8.1/solr-9.8.1.tgz | tar -xz --strip-components=1 -C /opt/solr

RUN echo "dbms.security.auth_enabled=false" >> /opt/neo4j/conf/neo4j.conf
RUN echo "server.jvm.additional=-Dorg.neo4j.kernel.impl.index.schema.GenericNativeIndexPopulator.blockBasedPopulation=true" >> /opt/neo4j/conf/neo4j.conf

# Copy all the code for dataload into /opt/dataload and build the JARs
#

RUN mkdir /opt/ols && mkdir /opt/ols/dataload && mkdir /opt/ols/ols-shared && mkdir /opt/uv_cache

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



####
#### EMBEDDINGS SUPPORT
####
RUN apt-get update && apt install -y build-essential cmake pigz vim
RUN cd /opt/ols/dataload/embeddings/ols_embed && cargo b --release && \
        cd /opt/ols/dataload/embeddings/ols_inject_hierarchy && cargo b --release

# To enable materialising embeddings using ChromaDB
# We have to use Debian buster as the base to run on the EBI HPC, so
# use uv to get Python 3.13 which is not available otherwise.
#
ENV UV_INSTALL_DIR="/usr/bin"
ENV UV_PYTHON_INSTALL_DIR="/usr/bin"
ENV UV_TOOL_DIR="/usr/bin"
ENV UV_TOOL_BIN_DIR="/usr/bin"
ENV UV_CACHE_DIR="/opt/uv_cache"
#ENV UV_PROJECT_ENVIRONMENT="/opt/uv_venv"
RUN curl -LsSf https://astral.sh/uv/install.sh | sh && \
        uv python install cpython-3.13.5-linux-x86_64-gnu
RUN cd /opt/ols/dataload/embeddings/ols_top_k && uv sync --frozen
####
####



RUN chown -R ols:ols /opt/* /tmp/out
RUN chmod -R 777 /opt/* /tmp/out

USER ols
WORKDIR /opt/ols/dataload

CMD ./dataload.dockersh



