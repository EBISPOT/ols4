import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.util.Set;

public class ReadJsonGatheringStrings {

    private static final JsonParser jsonParser = new JsonParser();

    public static void readAndGather(JsonReader jsonReader, Set<String> gatheredStrings) throws IOException {

        switch(jsonReader.peek()) {
            case BEGIN_ARRAY:
                readArray(jsonReader, gatheredStrings);
                break;
            case BEGIN_OBJECT:
                readObject(jsonReader, gatheredStrings);
                break;
            case STRING:
                String str = jsonReader.nextString();
                gatheredStrings.add(str);
                break;
            default:
                jsonReader.skipValue();
                break;
        }
    }

    private static void readArray(JsonReader jsonReader, Set<String> gatheredStrings) throws IOException {

        jsonReader.beginArray();

        while(jsonReader.peek() != JsonToken.END_ARRAY) {
            readAndGather(jsonReader, gatheredStrings);
        }

        jsonReader.endArray();
    }

    private static void readObject(JsonReader jsonReader, Set<String> gatheredStrings) throws IOException {

        jsonReader.beginObject();

        while(jsonReader.peek() != JsonToken.END_OBJECT) {

            String name = jsonReader.nextName();
            gatheredStrings.add(name);

            readAndGather(jsonReader, gatheredStrings);
        }

        jsonReader.endObject();
    }

}
