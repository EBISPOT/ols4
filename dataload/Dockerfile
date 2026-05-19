

FROM --platform=$BUILDPLATFORM rust:1.90.0-bullseye AS rust-builder
ARG TARGETPLATFORM
ARG BUILDPLATFORM

# Install cross-compilation toolchain when build != target
RUN set -eux; \
    case "$TARGETPLATFORM" in \
        "linux/amd64") RUST_TARGET=x86_64-unknown-linux-gnu; GCC_PKG="gcc-x86-64-linux-gnu libc6-dev-amd64-cross" ;; \
        "linux/arm64") RUST_TARGET=aarch64-unknown-linux-gnu; GCC_PKG="gcc-aarch64-linux-gnu libc6-dev-arm64-cross" ;; \
        *) echo "Unsupported TARGETPLATFORM: $TARGETPLATFORM" >&2; exit 1 ;; \
    esac; \
    echo "$RUST_TARGET" > /tmp/rust_target; \
    if [ "$TARGETPLATFORM" != "$BUILDPLATFORM" ]; then \
        apt-get update && apt-get install -y --no-install-recommends $GCC_PKG && rm -rf /var/lib/apt/lists/*; \
    fi; \
    rustup target add "$RUST_TARGET"

COPY ./dataload /opt/ols/dataload
COPY ./text_tagger /opt/ols/text_tagger

# Build all dataload Rust binaries (workspace) and text_tagger
RUN set -eux; \
    RUST_TARGET="$(cat /tmp/rust_target)"; \
    case "$RUST_TARGET" in \
        x86_64-unknown-linux-gnu) export CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER=x86_64-linux-gnu-gcc ;; \
        aarch64-unknown-linux-gnu) export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc ;; \
    esac; \
    cd /opt/ols/dataload && cargo build --release --target "$RUST_TARGET"; \
    mkdir -p /opt/ols/dataload/target/release; \
    for bin in ols_json2postgres ols_create_manifest ols_link extract_strings_from_terms descendants_centroid; do \
        cp "/opt/ols/dataload/target/$RUST_TARGET/release/$bin" "/opt/ols/dataload/target/release/$bin"; \
    done; \
    cd /opt/ols/text_tagger && cargo build --release --target "$RUST_TARGET"; \
    mkdir -p /opt/ols/text_tagger/target/release; \
    cp "/opt/ols/text_tagger/target/$RUST_TARGET/release/ols_text_tagger" /opt/ols/text_tagger/target/release/ols_text_tagger


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

# Install PostgreSQL 17 and pgvector for dataload
RUN apt-get update -y && apt-get install -y wget gnupg lsb-release && \
    echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list && \
    wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add - && \
    apt-get update -y && \
    apt-get install -y postgresql-17 postgresql-17-pgvector libnss-wrapper

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

# Copy pre-built Rust binaries from the cross-compile stage
COPY --from=rust-builder /opt/ols/dataload/target/release/ols_json2postgres /opt/ols/dataload/target/release/ols_json2postgres
COPY --from=rust-builder /opt/ols/dataload/target/release/ols_create_manifest /opt/ols/dataload/target/release/ols_create_manifest
COPY --from=rust-builder /opt/ols/dataload/target/release/ols_link /opt/ols/dataload/target/release/ols_link
COPY --from=rust-builder /opt/ols/dataload/target/release/extract_strings_from_terms /opt/ols/dataload/target/release/extract_strings_from_terms
COPY --from=rust-builder /opt/ols/dataload/target/release/descendants_centroid /opt/ols/dataload/target/release/descendants_centroid
COPY --from=rust-builder /opt/ols/text_tagger/target/release/ols_text_tagger /opt/ols/text_tagger/target/release/ols_text_tagger

# Install Rust binaries
RUN cp /opt/ols/dataload/target/release/ols_json2postgres /usr/local/bin/ && \
    cp /opt/ols/dataload/target/release/ols_create_manifest /usr/local/bin/ && \
    cp /opt/ols/dataload/target/release/ols_link /usr/local/bin/ && \
    cp /opt/ols/dataload/target/release/extract_strings_from_terms /usr/local/bin/ && \
    cp /opt/ols/dataload/target/release/descendants_centroid /usr/local/bin/ && \
    cp /opt/ols/text_tagger/target/release/ols_text_tagger /usr/local/bin/ && \
    chmod +x /usr/local/bin/ols_json2postgres /usr/local/bin/ols_create_manifest /usr/local/bin/ols_link /usr/local/bin/extract_strings_from_terms /usr/local/bin/descendants_centroid /usr/local/bin/ols_text_tagger

# COPY ./backend /opt/ols/backend
# RUN cd /opt/ols/backend && mvn -T 1C package



RUN mkdir /tmp/out


RUN chown -R ols:ols /opt/* /tmp/out
RUN chmod -R 777 /opt/* /tmp/out

USER ols
WORKDIR /opt/ols/dataload

CMD ./dataload.dockersh


