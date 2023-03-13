import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CopyJsonGatheringStrings {

    private static final JsonParser jsonParser = new JsonParser();

    private static final Pattern curiePattern = Pattern.compile("[A-Z]+:[0-9A-z]+");

    public static void copyJsonGatheringStrings(JsonReader jsonReader, JsonWriter jsonWriter, Set<String> gatheredStrings) throws IOException {

        switch(jsonReader.peek()) {
            case BEGIN_ARRAY:
                copyArray(jsonReader, jsonWriter, gatheredStrings);
                break;
            case BEGIN_OBJECT:
                copyObject(jsonReader, jsonWriter, gatheredStrings);
                break;
            case STRING:
                String str = jsonReader.nextString();

                gatheredStrings.add(str);

		Matcher matcher = curiePattern.matcher(str);

		while(matcher.find()) {
			gatheredStrings.add(matcher.group());
		}

                jsonWriter.value(str);
                break;
            default:
                JsonElement elem = jsonParser.parse(jsonReader);
                com.google.gson.internal.Streams.write(elem, jsonWriter);
                break;
        }
    }

    private static void copyArray(JsonReader jsonReader, JsonWriter jsonWriter, Set<String> gatheredStrings) throws IOException {

        jsonReader.beginArray();
        jsonWriter.beginArray();

        while(jsonReader.peek() != JsonToken.END_ARRAY) {
            copyJsonGatheringStrings(jsonReader, jsonWriter, gatheredStrings);
        }

        jsonReader.endArray();
        jsonWriter.endArray();
    }

    private static void copyObject(JsonReader jsonReader, JsonWriter jsonWriter, Set<String> gatheredStrings) throws IOException {

        jsonReader.beginObject();
        jsonWriter.beginObject();

        while(jsonReader.peek() != JsonToken.END_OBJECT) {

            String name = jsonReader.nextName();

            gatheredStrings.add(ExtractIriFromPropertyName.extract(name));

            jsonWriter.name(name);
            copyJsonGatheringStrings(jsonReader, jsonWriter, gatheredStrings);
        }

        jsonReader.endObject();
        jsonWriter.endObject();
    }



}
