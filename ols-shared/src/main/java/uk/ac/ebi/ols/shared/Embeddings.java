package uk.ac.ebi.ols.shared;
import java.io.*;
import java.util.*;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.example.data.Group;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

public class Embeddings {

    private Map<String, float[]> embeddingsCache;

    public Embeddings() {
        this.embeddingsCache = new HashMap<>();
    }
    
    public void loadEmbeddingsFromFile(String parquetPath) throws IOException {
        try {
            Configuration conf = new Configuration();
            Path path = new Path(parquetPath);
            
            GroupReadSupport readSupport = new GroupReadSupport();
            try (ParquetReader<Group> reader = ParquetReader.builder(readSupport, path)
                    .withConf(conf)
                    .build()) {
                
                Group group;
                while ((group = reader.read()) != null) {
                    String ontologyId = group.getString("ontology_id", 0);
                    String entityType = group.getString("entity_type", 0);
                    String iri = group.getString("iri", 0);
                    
                    // Read the embedding array
                    Group embeddingGroup = group.getGroup("embedding", 0);
                    int embeddingSize = embeddingGroup.getFieldRepetitionCount("list");
                    float[] embedding = new float[embeddingSize];
                    
                    for (int i = 0; i < embeddingSize; i++) {
                        Group element = embeddingGroup.getGroup("list", i);
                        embedding[i] = element.getFloat("element", 0);
                    }
                    
                    String key = makeKey(ontologyId, entityType, iri);
                    embeddingsCache.put(key, embedding);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.embeddingsCache = new HashMap<>();
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
