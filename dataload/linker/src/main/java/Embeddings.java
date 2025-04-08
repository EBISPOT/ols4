import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class Embeddings {

    static class CompositeKey {
        String ontologyId;
        String entityType;
        String iri;

        public CompositeKey(String ontologyId, String entityType, String iri) {
            this.ontologyId = ontologyId;
            this.entityType = entityType;
            this.iri = iri;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CompositeKey that = (CompositeKey) o;
            return ontologyId.equals(that.ontologyId) &&
                   entityType.equals(that.entityType) &&
                   iri.equals(that.iri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ontologyId, entityType, iri);
        }
    }

    private Map<CompositeKey, double[]> embeddingsMap = new HashMap<>();

    public void loadEmbeddingsFromFile(String gzippedFilePath) throws IOException {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(gzippedFilePath));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzipInputStream))) {

            String line;
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t");

                if (fields.length == 6) {
                    String ontologyId = fields[0];
                    String entityType = fields[1];
                    String iri = fields[2];
                    String embeddingsStr = fields[5];

                    String[] embeddingStrs = embeddingsStr.split(",");
                    double[] embeddings = new double[embeddingStrs.length];
                    for (int i = 0; i < embeddingStrs.length; i++) {
                        embeddings[i] = Double.parseDouble(embeddingStrs[i]);
                    }

                    CompositeKey key = new CompositeKey(ontologyId, entityType, iri);
                    embeddingsMap.put(key, embeddings);
                }
            }

            System.out.println("Loaded " + embeddingsMap.size() + " embeddings from " + gzippedFilePath);
        }
    }

    public double[] getEmbeddings(String ontologyId, String entityType, String iri) {
        CompositeKey key = new CompositeKey(ontologyId, entityType, iri);
        return embeddingsMap.get(key);
    }
}
