
import org.iq80.leveldb.*;
import static org.fusesource.leveldbjni.JniDBFactory.*;
import java.io.*;

public class LevelDB {

    private DB db;

    public LevelDB(String path) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        db = factory.open(new File(path), options);
    }

    public void put(String key, String value) {
        db.put(bytes(key), bytes(value));
    }

    public void close() throws IOException {
        db.close();
    }
}
