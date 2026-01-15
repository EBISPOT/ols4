package uk.ac.ebi.ols.shared;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.io.SeekableInputStream;
import org.apache.parquet.schema.MessageType;

public class Embeddings {

    public Map<String, float[]> embeddingsCache;

    public Embeddings() {
        this.embeddingsCache = new HashMap<>();
    }

    public void loadEmbeddingsFromFile(String parquetPath, String filterByOntologyId) throws IOException {
        InputFile inputFile = new LocalInputFile(new File(parquetPath));
        
        try (ParquetFileReader reader = ParquetFileReader.open(inputFile)) {
            MessageType schema = reader.getFooter().getFileMetaData().getSchema();
            
            PageReadStore pages;
            while ((pages = reader.readNextRowGroup()) != null) {
                long rows = pages.getRowCount();
                MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
                RecordReader<Group> recordReader = columnIO.getRecordReader(pages, new GroupRecordConverter(schema));
                
                for (int i = 0; i < rows; i++) {
                    Group group = recordReader.read();
                    String ontologyId = group.getString("ontology_id", 0);
                    
                    // Filter by ontology ID if specified
                    if (filterByOntologyId != null && !filterByOntologyId.isEmpty() 
                        && !ontologyId.equals(filterByOntologyId)) {
                        continue;
                    }

                    String entityType = group.getString("entity_type", 0);
                    String iri = group.getString("iri", 0);

                    Group embeddingGroup = group.getGroup("embedding", 0);
                    int embeddingSize = embeddingGroup.getFieldRepetitionCount("list");

                    float[] embedding = new float[embeddingSize];
                    for (int j = 0; j < embeddingSize; j++) {
                        embedding[j] = embeddingGroup.getGroup("list", j).getFloat("element", 0);
                    }

                    embeddingsCache.put(makeKey(ontologyId, entityType, iri), embedding);
                }
            }
        }
    }

    private String makeKey(String ontologyId, String entityType, String iri) {
        return ontologyId + "|" + entityType + "|" + iri;
    }

    public float[] getEmbeddings(String ontologyId, String entityType, String iri) {
        if (this.embeddingsCache == null) {
            return null;
        }
        return embeddingsCache.get(makeKey(ontologyId, entityType, iri));
    }

    // Local file InputFile implementation - no Hadoop required
    private static class LocalInputFile implements InputFile {
        private final File file;

        LocalInputFile(File file) {
            this.file = file;
        }

        @Override
        public long getLength() throws IOException {
            return file.length();
        }

        @Override
        public SeekableInputStream newStream() throws IOException {
            return new LocalSeekableInputStream(file);
        }
    }

    private static class LocalSeekableInputStream extends SeekableInputStream {
        private final RandomAccessFile raf;

        LocalSeekableInputStream(File file) throws IOException {
            this.raf = new RandomAccessFile(file, "r");
        }

        @Override
        public long getPos() throws IOException {
            return raf.getFilePointer();
        }

        @Override
        public void seek(long newPos) throws IOException {
            raf.seek(newPos);
        }

        @Override
        public int read() throws IOException {
            return raf.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return raf.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }

        @Override
        public void readFully(byte[] bytes) throws IOException {
            raf.readFully(bytes);
        }

        @Override
        public void readFully(byte[] bytes, int start, int len) throws IOException {
            raf.readFully(bytes, start, len);
        }

        @Override
        public void readFully(ByteBuffer buf) throws IOException {
            byte[] bytes = new byte[buf.remaining()];
            raf.readFully(bytes);
            buf.put(bytes);
        }

        @Override
        public int read(ByteBuffer buf) throws IOException {
            byte[] bytes = new byte[buf.remaining()];
            int bytesRead = raf.read(bytes);
            if (bytesRead > 0) {
                buf.put(bytes, 0, bytesRead);
            }
            return bytesRead;
        }
    }
}
