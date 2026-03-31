

FROM rust:1.90.0-bullseye

RUN apt-get update -y && apt-get install -y pigz python3-requests python3-pip

RUN pip3 install pyarrow

# Install Amazon Corretto 21 with multi-arch support
RUN ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "amd64" ]; then \
        curl https://corretto.aws/downloads/resources/21.0.6.7.1/amazon-corretto-21.0.6.7.1-linux-x64.tar.gz | tar -C /opt -xzf - && \
        echo 'export JAVA_HOME=/opt/amazon-corretto-21.0.6.7.1-linux-x64' >> ~/.bashrc && \
        echo 'export PATH=$PATH:$JAVA_HOME/bin' >> ~/.bashrc && \
        ln -s /opt/amazon-corretto-21.0.6.7.1-linux-x64 /opt/java; \
    elif [ "$ARCH" = "arm64" ]; then \
        curl https://corretto.aws/downloads/resources/21.0.6.7.1/amazon-corretto-21.0.6.7.1-linux-aarch64.tar.gz | tar -C /opt -xzf - && \
        echo 'export JAVA_HOME=/opt/amazon-corretto-21.0.6.7.1-linux-aarch64' >> ~/.bashrc && \
        echo 'export PATH=$PATH:$JAVA_HOME/bin' >> ~/.bashrc && \
        ln -s /opt/amazon-corretto-21.0.6.7.1-linux-aarch64 /opt/java; \
    fi
ENV JAVA_HOME="/opt/java"
ENV PATH="$PATH:/opt/java/bin"

RUN addgroup --system --gid 1000 ols && adduser --system --uid 1000 --ingroup ols ols

# Install Maven
#
RUN curl https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz | tar -C /usr/local --strip-components=1 -xzf -

# Extract Solr vanilla install to /opt/solr
#
# We use this only as a temporary server for the dataload. The "real"
# Solr server is the standard image specified in docker-compose.yml.
# It is important that the versions match!
#
RUN mkdir /opt/solr && \
    curl https://ftp.ebi.ac.uk/pub/databases/spot/mirror/solr-9.8.1.tgz | tar -xz --strip-components=1 -C /opt/solr

# Install PostgreSQL 17 and pgvector for dataload
RUN apt-get update -y && apt-get install -y wget gnupg lsb-release libnss-wrapper && \
    echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list && \
    wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add - && \
    apt-get update -y && \
    apt-get install -y postgresql-17 postgresql-17-pgvector

# Copy all the code for dataload into /opt/dataload and build the JARs
#

RUN mkdir /opt/ols && mkdir /opt/ols/dataload && mkdir /opt/ols/ols-shared

COPY ./dataload /opt/ols/dataload
COPY ./text_tagger /opt/ols/text_tagger
COPY ./ols-shared /opt/ols/ols-shared
COPY ./pom.xml /opt/ols

COPY ./test_dataload.sh /opt/ols
COPY ./dev-testing /opt/ols/dev-testing
COPY ./testcases_expected_output /opt/ols/testcases_expected_output
COPY ./testcases /opt/ols/testcases

RUN cd /opt/ols/ols-shared && mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn package

RUN mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn install:install-file -DcreateChecksum=true -Dpackaging=jar -Dfile=/opt/ols/ols-shared/target/ols4-shared-1.0.0-SNAPSHOT.jar -DgroupId=uk.ac.ebi.spot.ols -DartifactId=ols4-shared -Dversion=1.0.0-SNAPSHOT
RUN cd /opt/ols/dataload &&  mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -T 1C package

# Build all Rust binaries
RUN cd /opt/ols/dataload && cargo build --release

# Build text_tagger
RUN cd /opt/ols/text_tagger && cargo build --release

# Install Rust binaries
RUN cp /opt/ols/dataload/target/release/ols_json2postgres /usr/local/bin/ && \
    cp /opt/ols/dataload/target/release/ols_create_manifest /usr/local/bin/ && \
    cp /opt/ols/dataload/target/release/ols_link /usr/local/bin/ && \
    cp /opt/ols/dataload/target/release/ols_json2solr /usr/local/bin/ && \
    cp /opt/ols/dataload/target/release/extract_strings_from_terms /usr/local/bin/ && \
    cp /opt/ols/text_tagger/target/release/ols_text_tagger /usr/local/bin/ && \
    chmod +x /usr/local/bin/ols_json2postgres /usr/local/bin/ols_create_manifest /usr/local/bin/ols_link /usr/local/bin/ols_json2solr /usr/local/bin/extract_strings_from_terms /usr/local/bin/ols_text_tagger

# COPY ./backend /opt/ols/backend
# RUN cd /opt/ols/backend && mvn -T 1C package



RUN mkdir /tmp/out


RUN chown -R ols:ols /opt/* /tmp/out
RUN chmod -R 777 /opt/* /tmp/out

USER ols
WORKDIR /opt/ols/dataload

CMD ./dataload.dockersh



