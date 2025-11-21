package uk.ac.ebi.ols.shared;
import java.io.*;
import java.util.*;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.example.data.Group;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.filter2.compat.FilterCompat;
import org.apache.parquet.filter2.predicate.FilterPredicate;
import org.apache.parquet.io.api.Binary;
import static org.apache.parquet.filter2.predicate.FilterApi.*;

public class Embeddings {

    public Map<String, float[]> embeddingsCache;

    public Embeddings() {
        this.embeddingsCache = new HashMap<>();
    }

public void loadEmbeddingsFromFile(String parquetPath, String filterByOntologyId) throws IOException {
    Configuration conf = new Configuration();
    Path path = new Path(parquetPath);

    FilterPredicate filter = eq(binaryColumn("ontology_id"), Binary.fromString(filterByOntologyId));
    
    GroupReadSupport readSupport = new GroupReadSupport();

    try (ParquetReader<Group> reader = ParquetReader.builder(readSupport, path)
            .withConf(conf)
            .withFilter(FilterCompat.get(filter))
            .build()) {

        Group group;
        while ((group = reader.read()) != null) {
            String ontologyId = group.getString("ontology_id", 0);
            String entityType = group.getString("entity_type", 0);
            String iri = group.getString("iri", 0);

            Group embeddingGroup = group.getGroup("embedding", 0);
            int embeddingSize = embeddingGroup.getFieldRepetitionCount("list");

            float[] embedding = new float[embeddingSize];
            for (int i = 0; i < embeddingSize; i++) {
                embedding[i] = embeddingGroup.getGroup("list", i).getFloat("element", 0);
            }

            embeddingsCache.put(makeKey(ontologyId, entityType, iri), embedding);
        }
    }
}

    
    private String makeKey(String ontologyId, String entityType, String iri) {
        return ontologyId + "|" + entityType + "|" + iri;
    }

    public float[] getEmbeddings(String ontologyId, String entityType, String iri) {
        if(this.embeddingsCache == null) {
            return null;
        }
        
        String key = makeKey(ontologyId, entityType, iri);
        return embeddingsCache.get(key);
    }
}
