
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.iq80.leveldb.*;
import static org.fusesource.leveldbjni.JniDBFactory.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class LevelDB {

    private DB db;

    final JsonParser jsonParser = new JsonParser();

    public LevelDB(String path) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        db = factory.open(new File(path), options);
    }

    public JsonElement get(String key) throws UnsupportedEncodingException {
        byte[] res = db.get(bytes(key));
        if(res != null) {
            return jsonParser.parse(new String(res, StandardCharsets.UTF_8));
        }
        return null;
    }

    public void close() throws IOException {
        db.close();
    }
}
