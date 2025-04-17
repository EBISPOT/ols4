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
import com.google.gson.JsonElement;

public class Embeddings {

    private Connection connection;
    private Gson gson;
    private PreparedStatement stmt;

    public Embeddings() {
        this.gson = new Gson();
    }
    
        public void loadEmbeddingsFromFile(String sqlitePath) throws IOException {
    
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            config.setOpenMode(SQLiteOpenMode.READONLY);
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath, config.toProperties());
            this.stmt = this.connection.prepareStatement(
                "SELECT embeddings FROM embeddings WHERE ontologyId = ? AND entityType = ? AND iri = ?"
            );

        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

    }

    public double[] getEmbeddings(String ontologyId, String entityType, String iri) {

        if(this.connection == null) {
            return null;
        }

        try {

            this.stmt.setString(1, ontologyId);
            this.stmt.setString(2, entityType);
            this.stmt.setString(3, iri);
            var rs = this.stmt.executeQuery();
            if (rs.next()) {
                String embeddingString = rs.getString("embeddings");

                if(embeddingString.startsWith("{")) {
                    // this is a temporary hack as we have two formats of embeddings in the 300 GB
                    // database and would be expensive to re-embed everything. TODO: manually
                    // patch the existing embeddings in the DB to all be the same.
                    JsonElement jsonElement = gson.fromJson(embeddingString, JsonElement.class);
                    return gson.fromJson(jsonElement.getAsJsonObject().get("embedding"), double[].class);
                } else {
                    return gson.fromJson(embeddingString, double[].class);
                }

            } else {
                return null;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }


    }
}
