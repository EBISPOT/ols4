import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import com.google.gson.Gson;

public class Embeddings {

    private Connection connection;
    private Gson gson;

    public Embeddings() {
        this.gson = new Gson();
    }
    
        public void loadEmbeddingsFromFile(String sqlitePath) throws IOException {
    
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            config.setOpenMode(SQLiteOpenMode.READONLY);
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath, config.toProperties());
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
    }

    public double[] getEmbeddings(String ontologyId, String entityType, String iri) {

        try {

            var stmt = this.connection.prepareStatement("SELECT embeddings FROM embeddings WHERE ontologyId = ? AND entityType = ? AND iri = ?");

            stmt.setString(1, ontologyId);
            stmt.setString(2, entityType);
            stmt.setString(3, iri);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                String embeddingString = rs.getString("embeddings");
                return gson.fromJson(embeddingString, double[].class);
            } else {
                return null;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }


    }
}
