package uk.ac.ebi.spot.ols.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage for {@link PostgresClient#decompressJson(byte[])}. This is the only one of
 * the three {@code decompressJson} overloads reachable without a real Postgres {@link
 * java.sql.ResultSet}; the two {@code ResultSet}-taking overloads are covered end-to-end against a
 * real BYTEA column in {@code PostgresClientIT} instead (see
 * docs/backend-testing-strategy.md's "Implemented PostgresClient baseline" section for why).
 *
 * <p>{@link PostgresClient} itself is a {@code @Component} with {@code @Value}-injected connection
 * settings and a {@code @PostConstruct} pool-initialization method, so this class has no
 * constructor-time behaviour worth exercising here; {@code init()}/{@code getConnection()}/
 * {@code dsl()}/{@code returnNodeCount()} all genuinely need a real Postgres connection and are
 * covered in the IT layer.
 */
class PostgresClientTest {

    /**
     * Builds a genuinely gzip-compressed fixture the same way production code does (never
     * hand-crafted bytes), mirroring {@code PostgresIntegrationTestSupport}'s private {@code
     * gzip(String)} helper.
     */
    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    @Test
    void decompressJsonReturnsNullForNullByteArrayInsteadOfThrowing() throws SQLException {
        assertThat(PostgresClient.decompressJson((byte[]) null)).isNull();
    }

    @Test
    void decompressJsonRoundTripsAGenuinelyGzipCompressedString() throws Exception {
        String original = "{\"id\":\"efo+class+http://example.org/EFO_0001\",\"label\":\"Liver disease\"}";

        String decompressed = PostgresClient.decompressJson(gzip(original));

        assertThat(decompressed).isEqualTo(original);
    }

    @Test
    void decompressJsonWrapsCorruptOrNonGzipBytesInASqlException() {
        byte[] notGzip = "this is not gzip-compressed data at all".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PostgresClient.decompressJson(notGzip))
                .isInstanceOf(SQLException.class)
                .hasMessage("Failed to decompress _json")
                .hasCauseInstanceOf(java.io.IOException.class);
    }

    @Test
    void decompressJsonWrapsTruncatedGzipStreamInASqlExceptionTooDeepInTheReadLoop() throws Exception {
        // A well-formed gzip header followed by truncated/corrupt compressed payload: this fails
        // inside the read loop itself (after the stream has already started producing output),
        // not at construction time, exercising a different failure point than a bare non-gzip
        // byte array does.
        byte[] valid = gzip("a".repeat(20_000));
        byte[] truncated = new byte[valid.length / 2];
        System.arraycopy(valid, 0, truncated, 0, truncated.length);

        assertThatThrownBy(() -> PostgresClient.decompressJson(truncated))
                .isInstanceOf(SQLException.class)
                .hasMessage("Failed to decompress _json")
                .hasCauseInstanceOf(java.io.IOException.class);
    }

    @Test
    void closeIsSafeToCallOnAClientThatWasNeverInitialized() {
        // close()'s dataSource != null guard exists for exactly this case: a bean that never
        // completed init() (or is closed twice) must not NPE on shutdown.
        new PostgresClient().close();
    }

    @Test
    void decompressJsonHandlesStringsLargerThanTheReadBufferAcrossMultipleReadCalls() throws Exception {
        // The internal char[] buf is exactly 8192 chars; a decompressed string well beyond that
        // forces reader.read(buf) to be called more than once to drain the stream, not just the
        // single-pass case a short string would exercise.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            builder.append("\"line-").append(i).append("\":\"value-").append(i).append("\",");
        }
        String original = builder.toString();
        assertThat(original.length()).isGreaterThan(8192 * 2);

        String decompressed = PostgresClient.decompressJson(gzip(original));

        assertThat(decompressed).isEqualTo(original);
        assertThat(decompressed.length()).isEqualTo(original.length());
    }
}
