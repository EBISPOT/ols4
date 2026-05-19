
FROM --platform=$BUILDPLATFORM rust:1.90.0-bullseye AS rust-builder
ARG TARGETPLATFORM
ARG BUILDPLATFORM

# Install cross-compilation toolchain when build != target
RUN set -eux; \
    case "$TARGETPLATFORM" in \
        "linux/amd64") RUST_TARGET=x86_64-unknown-linux-gnu; DPKG_ARCH=amd64; GCC_PKG="gcc-x86-64-linux-gnu libc6-dev-amd64-cross" ;; \
        "linux/arm64") RUST_TARGET=aarch64-unknown-linux-gnu; DPKG_ARCH=arm64; GCC_PKG="gcc-aarch64-linux-gnu libc6-dev-arm64-cross" ;; \
        *) echo "Unsupported TARGETPLATFORM: $TARGETPLATFORM" >&2; exit 1 ;; \
    esac; \
    echo "$RUST_TARGET" > /tmp/rust_target; \
    if [ "$TARGETPLATFORM" != "$BUILDPLATFORM" ]; then \
        apt-get update && apt-get install -y --no-install-recommends $GCC_PKG && rm -rf /var/lib/apt/lists/*; \
    fi; \
    rustup target add "$RUST_TARGET"

COPY ./text_tagger /opt/text_tagger
RUN set -eux; \
    RUST_TARGET="$(cat /tmp/rust_target)"; \
    case "$RUST_TARGET" in \
        x86_64-unknown-linux-gnu) export CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER=x86_64-linux-gnu-gcc ;; \
        aarch64-unknown-linux-gnu) export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc ;; \
    esac; \
    cd /opt/text_tagger && cargo build --release --target "$RUST_TARGET"; \
    mkdir -p /opt/text_tagger/target/release; \
    cp "/opt/text_tagger/target/$RUST_TARGET/release/ols_text_tagger" /opt/text_tagger/target/release/ols_text_tagger

FROM maven:3.9.10-eclipse-temurin-24

# Copy text_tagger binary from rust builder
COPY --from=rust-builder /opt/text_tagger/target/release/ols_text_tagger /usr/local/bin/ols_text_tagger
RUN chmod +x /usr/local/bin/ols_text_tagger

RUN mkdir /opt/ols && mkdir /opt/ols/backend && mkdir /opt/ols/ols-shared
COPY ./backend /opt/ols/backend
COPY ./ols-shared /opt/ols/ols-shared

RUN cd /opt/ols/ols-shared && mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn package

RUN mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn install:install-file -DcreateChecksum=true -Dpackaging=jar -Dfile=/opt/ols/ols-shared/target/ols4-shared-1.0.0-SNAPSHOT.jar -DgroupId=uk.ac.ebi.spot.ols -DartifactId=ols4-shared -Dversion=1.0.0-SNAPSHOT
RUN cd /opt/ols/backend && ls && mvn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn clean package -DskipTests

EXPOSE 8080
ENTRYPOINT ["java", "--add-opens=java.base/java.util=ALL-UNNAMED", "-jar", "/opt/ols/backend/target/ols4-backend-4.0.0-SNAPSHOT.jar"]
