package uk.ac.ebi.ols.apitester;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public class RecursiveJsonDiff {


    String inputDir, expectedDir;
    Gson gson = new Gson();

    public RecursiveJsonDiff(String inputDir, String expectedDir) {
        this.inputDir = inputDir;
        this.expectedDir = expectedDir;
    }

    public boolean diff() throws FileNotFoundException {

        boolean success = true;

        LinkedHashSet<String> inputFiles = getJsonFiles(inputDir);
        LinkedHashSet<String> expectedFiles = getJsonFiles(expectedDir);

        for(String filename : inputFiles) {
            if(!expectedFiles.contains(filename)) {
                System.out.println("In output but not expected: " + filename);
                success = false;
            }
        }

        for(String filename : expectedFiles) {
            if(!inputFiles.contains(filename)) {
                System.out.println("In expected but not output: " + filename);
                success = false;
            }
        }

        for(String filename : inputFiles) {

            if(!expectedFiles.contains(filename)) {
                // nothing to compare
                continue;
            }

            JsonElement jsonA = readJsonFile(inputDir + filename);
            JsonElement jsonB = readJsonFile(expectedDir + filename);

            if(jsonA.isJsonObject() && jsonA.getAsJsonObject().has("error") &&
                    jsonB.isJsonObject() && jsonB.getAsJsonObject().has("error")) {
                System.out.println(filename + ": both input and expected were an error. This tool does not diff the contents of the error.");
                continue;
            }

            if(jsonA.equals(jsonB)) {
                System.out.println(inputDir + filename + " was equal to " + expectedDir + filename);
            } else {
                System.out.println(inputDir + filename + " differed from " + expectedDir + filename);
                success = false;
            }

        }

        return success;
    }

    LinkedHashSet<String> getJsonFiles(String path) {
        System.out.println(path);
        if (path.startsWith("./"))
            path = path.substring("./".length());
        String finalPath = path;
        Collection<File> files = FileUtils.listFiles(new File(path), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        return new LinkedHashSet<>(
                files.stream().map(file -> {
                            String filePath = file.getPath();
                            if(!filePath.startsWith(finalPath)) {
                                throw new RuntimeException("File " + filePath + " + did not start with prefix " + finalPath);
                            }

                            return filePath.substring(finalPath.length());
                        }).filter(filePath -> {
                            return filePath.endsWith(".json");
                        })
                        .collect(Collectors.toList())
        );
    }

    JsonElement readJsonFile(String filename) throws FileNotFoundException {
        JsonReader reader = new JsonReader(new FileReader(filename));
        return JsonParser.parseReader(reader);
    }
}