package uk.ac.ebi.ols.shared;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.sql.Array;

public class Embeddings {

    private Connection connection;
    private PreparedStatement stmt;

    public Embeddings() {
    }
    
    public void loadEmbeddingsFromFile(String duckdbPath) throws IOException {
    
        try {
            Properties readOnlyProperty = new Properties();
            readOnlyProperty.setProperty("duckdb.read_only", "true");
            this.connection = DriverManager.getConnection("jdbc:duckdb:" + duckdbPath, readOnlyProperty);
            this.stmt = this.connection.prepareStatement(
                "SELECT embedding FROM terms_embedded WHERE ontology_id = ? AND entity_type = ? AND iri = ?"
            );

        } catch (SQLException e) {
            e.printStackTrace();
            this.connection = null;
            this.stmt = null;
            return;
        }

    }

    public float[] getEmbeddings(String ontologyId, String entityType, String iri) {

        if(this.connection == null) {
            return null;
        }

        try {

            this.stmt.setString(1, ontologyId);
            this.stmt.setString(2, entityType);
            this.stmt.setString(3, iri);
            var rs = this.stmt.executeQuery();
            if (rs.next()) {
                Array sqlArray = rs.getArray("embedding");
                if (sqlArray == null) {
                    return null;
                }
                
                Object[] objArray = (Object[]) sqlArray.getArray();
                float[] result = new float[objArray.length];
                for (int i = 0; i < objArray.length; i++) {
                    result[i] = ((Number) objArray[i]).floatValue();
                }
                return result;

            } else {
                return null;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }


    }
}
