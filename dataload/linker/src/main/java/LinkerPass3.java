import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class LinkerPass3 {

    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /*
   Scan through the JSON again.
For each distinct set of ontology IDs from step (2) load the referenced ontologies using map (i) from the first step.
For each ontology that requires the above set, output the ontology, linking as we go using O(1) lookups in maps (ii) then (i) from the first step
     */
    public static void run(
            String inputJsonFilename,
            String outputJsonFilename,
            LinkerPass1.LinkerPass1Result pass1Result,
            LinkerPass2.LinkerPass2Result pass2Result) throws IOException {

        System.out.println("--- Linker Pass 3: Linking " + inputJsonFilename + " to create " + outputJsonFilename);

        // Invert (ontologyId -> referencedOntologyIds[]) to (referencedOntologyIds[] -> ontologyId[])
        // This way we can (a) load each distinct set of referenced ontologies  then (b) load the ontologies that
        // reference that set
        //
        var distinctReferencedSetToOntologyIds = new HashMap<Set<String>, Set<String>>();
        for(var entry : pass2Result.ontologyIdToReferences.entrySet()) {
            var existing = distinctReferencedSetToOntologyIds.get(entry.getValue());
            if(existing != null) {
                existing.add(entry.getKey());
            } else {
                var ids = new TreeSet<String>();
                ids.add(entry.getKey());
                distinctReferencedSetToOntologyIds.put(entry.getValue(), ids);
            }
        }

        // Sort the distinct sets of referenced ontology IDs so the supersets are at the end.
        // The resulting sorted list will have e.g. {ro} at the beginning for ontologies that just load ro, then {ro,duo}
        // for ontologies that load both ro and duo. Then we don't need to load ontologies multiple times unnecessarily.
        //
        var sortedDistinctSets = distinctReferencedSetToOntologyIds.keySet().stream().sorted((o1, o2) -> {
            if(o1.equals(o2))
                return 0;
            return o1.containsAll(o2) ? 1 : -1;
        }).collect(Collectors.toList());


        int maxN = 0;
        for(var ont : sortedDistinctSets) {
            maxN = Math.max(maxN, ont.size());
        }

        System.out.println("Found " + sortedDistinctSets.size() + " distinct sets of referenced ontology IDs (max number referenced: " + maxN + ")");

        Files.write(Path.of("/Users/james/ols4/foo.json"), gson.toJson(sortedDistinctSets).getBytes(StandardCharsets.UTF_8));



    }
}
