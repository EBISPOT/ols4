package uk.ac.ebi.spot.ols.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service that wraps the ols_text_tagger CLI binary.
 *
 * <p>The CLI runs in interactive mode: it reads one line of text from stdin
 * and writes one JSON object per line to stdout.  Because the process is
 * stateful (single stdin/stdout pair) we serialise access with a lock so
 * the multithreaded backend doesn't interleave requests.</p>
 *
 * <p>If the binary is not found on the PATH or no database file is
 * configured the service degrades gracefully &mdash; {@link #isAvailable()}
 * returns {@code false} and {@link #tagText} returns an empty list.</p>
 */
@Service
public class TextTaggerService {

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    /**
     * A single entity match returned by the text tagger.
     */
    public static class TaggedEntity {
        public final int start;
        public final int end;
        public final String termLabel;
        public final String termIri;
        public final String ontologyId;
        public final String stringType;
        public final String source;
        public final List<String> subjectCategories;

        public TaggedEntity(int start, int end, String termLabel, String termIri, String ontologyId,
                            String stringType, String source,
                            List<String> subjectCategories) {
            this.start = start;
            this.end = end;
            this.termLabel = termLabel;
            this.termIri = termIri;
            this.ontologyId = ontologyId;
            this.stringType = stringType;
            this.source = source;
            this.subjectCategories = subjectCategories;
        }
    }

    private static final String BINARY_NAME = "ols_text_tagger";

    @Value("${ols.text.tagger.db:#{null}}")
    private String dbPath;

    private Process process;
    private BufferedWriter processStdin;
    private BufferedReader processStdout;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean available = false;
    private final Gson gson = new Gson();

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @PostConstruct
    public void init() {
        if (dbPath == null || dbPath.isEmpty()) {
            System.err.println("TextTaggerService: no database path configured (ols.text_tagger.db) \u2013 disabled");
            return;
        }
        try {
            startProcess(null);
        } catch (Exception e) {
            System.err.println("TextTaggerService: failed to start \u2013 " + e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        stopProcess();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public boolean isAvailable() {
        return available;
    }

    /**
     * Tag free text and return matching ontology entities.
     *
     * <p>When {@code priorityOntologyIds} is provided the results are
     * filtered and prioritised: for each (start, end) span only the
     * match whose ontology appears earliest in the priority list is
     * kept.  Matches from ontologies not in the list are dropped.</p>
     *
     * @param text                the input text to annotate
     * @param priorityOntologyIds ordered list of ontology IDs (highest priority first), or null for all
     * @param delimiters          optional delimiter characters for word-boundary matching
     * @param minLength           minimum matched text length (inclusive); matches shorter than this are dropped
     * @param includeSubstrings   if false, when one match's span is entirely contained within another's, the shorter is removed
     * @return list of entity matches (empty if service unavailable)
     */
    public List<TaggedEntity> tagText(String text, List<String> priorityOntologyIds, String delimiters, int minLength, boolean includeSubstrings) {
        return tagText(text, priorityOntologyIds, null, delimiters, minLength, includeSubstrings);
    }

    /**
     * Tag free text and return matching ontology entities.
     *
     * @param text                the input text to annotate
     * @param priorityOntologyIds ordered list of ontology IDs (highest priority first), or null for all
     * @param sources             if non-null/non-empty, only keep matches from these sources (entities with null source are always kept)
     * @param delimiters          optional delimiter characters for word-boundary matching
     * @param minLength           minimum matched text length (inclusive); matches shorter than this are dropped
     * @param includeSubstrings   if false, when one match's span is entirely contained within another's, the shorter is removed
     * @return list of entity matches (empty if service unavailable)
     */
    public List<TaggedEntity> tagText(String text, List<String> priorityOntologyIds, List<String> sources, String delimiters, int minLength, boolean includeSubstrings) {
        if (!available) {
            return Collections.emptyList();
        }

        lock.lock();
        try {
            ensureRunning(delimiters);

            // Write one line (newlines in text must be escaped)
            String sanitised = text.replace("\n", " ").replace("\r", " ");
            processStdin.write(sanitised);
            processStdin.newLine();
            processStdin.flush();

            // Read exactly one JSON line back
            String responseLine = processStdout.readLine();
            if (responseLine == null) {
                System.err.println("TextTaggerService: process returned null \u2013 restarting");
                restartProcess(delimiters);
                return Collections.emptyList();
            }

            List<TaggedEntity> entities = parseResponse(responseLine);
            entities = applyMinLength(entities, minLength);
            if (!includeSubstrings) {
                entities = removeSubstrings(entities);
            }
            entities = applySourceFilter(entities, sources);
            return applyPriority(entities, priorityOntologyIds);

        } catch (IOException e) {
            System.err.println("TextTaggerService: I/O error \u2013 " + e.getMessage());
            try { restartProcess(delimiters); } catch (Exception ignored) {}
            return Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Backwards-compatible overload without minLength/includeSubstrings.
     */
    public List<TaggedEntity> tagText(String text, List<String> priorityOntologyIds, String delimiters) {
        return tagText(text, priorityOntologyIds, delimiters, 3, true);
    }

    // ------------------------------------------------------------------
    // Process management
    // ------------------------------------------------------------------

    private String currentDelimiters = null;

    private void startProcess(String delimiters) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(BINARY_NAME);
        cmd.add("cli");
        cmd.add(dbPath);
        if (delimiters != null && !delimiters.isEmpty()) {
            cmd.add("--delimiters");
            cmd.add(delimiters);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        process = pb.start();
        processStdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        processStdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        currentDelimiters = delimiters;
        available = true;

        System.err.println("TextTaggerService: started process with db=" + dbPath
                + (delimiters != null ? " delimiters=" + delimiters : ""));
    }

    private void stopProcess() {
        available = false;
        if (process != null) {
            try { processStdin.close(); } catch (Exception ignored) {}
            try { processStdout.close(); } catch (Exception ignored) {}
            process.destroyForcibly();
            process = null;
        }
    }

    private void restartProcess(String delimiters) {
        stopProcess();
        try {
            startProcess(delimiters);
        } catch (Exception e) {
            System.err.println("TextTaggerService: restart failed \u2013 " + e.getMessage());
        }
    }

    private void ensureRunning(String delimiters) throws IOException {
        boolean delimChanged = !Objects.equals(delimiters, currentDelimiters);
        boolean dead = process == null || !process.isAlive();
        if (delimChanged || dead) {
            stopProcess();
            startProcess(delimiters);
        }
    }

    // ------------------------------------------------------------------
    // Response parsing
    // ------------------------------------------------------------------

    private List<TaggedEntity> parseResponse(String json) {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonArray entities = root.getAsJsonArray("entities");
        if (entities == null) {
            return Collections.emptyList();
        }

        List<TaggedEntity> results = new ArrayList<>(entities.size());
        for (JsonElement el : entities) {
            JsonObject e = el.getAsJsonObject();
            String stringType = e.has("string_type") ? e.get("string_type").getAsString() : null;
            String source = e.has("source") ? e.get("source").getAsString() : null;
            List<String> subjectCategories = jsonArrayToStringList(e, "subject_categories");
            results.add(new TaggedEntity(
                    e.get("start").getAsInt(),
                    e.get("end").getAsInt(),
                    e.has("term_label") ? e.get("term_label").getAsString() : "",
                    e.has("term_iri")   ? e.get("term_iri").getAsString()   : "",
                    e.has("ontology_id") ? e.get("ontology_id").getAsString() : "",
                    stringType,
                    source,
                    subjectCategories
            ));
        }
        return results;
    }

    private List<String> jsonArrayToStringList(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement val = obj.get(key);
        if (!val.isJsonArray()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (JsonElement el : val.getAsJsonArray()) {
            result.add(el.getAsString());
        }
        return result.isEmpty() ? null : result;
    }

    // ------------------------------------------------------------------
    // Priority-based filtering
    // ------------------------------------------------------------------

    /**
     * For each unique (start, end) span, keep only the match whose
     * ontology appears earliest in {@code priorityOntologyIds}.
     * Matches from ontologies not in the list are dropped.
     * If the list is null or empty, all matches are returned as-is.
     */
    private List<TaggedEntity> applyPriority(List<TaggedEntity> entities, List<String> priorityOntologyIds) {
        if (priorityOntologyIds == null || priorityOntologyIds.isEmpty()) {
            return entities;
        }

        // Build a lookup: ontologyId -> priority index (lower = higher priority)
        Map<String, Integer> priorityMap = new HashMap<>(priorityOntologyIds.size());
        for (int i = 0; i < priorityOntologyIds.size(); i++) {
            priorityMap.putIfAbsent(priorityOntologyIds.get(i), i);
        }

        // Group by (start, end) span, keeping only the best match per span
        // Use a LinkedHashMap to preserve encounter order
        Map<Long, TaggedEntity> best = new LinkedHashMap<>();
        Map<Long, Integer> bestPriority = new HashMap<>();

        for (TaggedEntity entity : entities) {
            Integer prio = priorityMap.get(entity.ontologyId);
            if (prio == null) {
                continue; // not in priority list \u2013 drop
            }

            long key = spanKey(entity.start, entity.end);
            Integer currentBest = bestPriority.get(key);
            if (currentBest == null || prio < currentBest) {
                best.put(key, entity);
                bestPriority.put(key, prio);
            }
        }

        return new ArrayList<>(best.values());
    }

    private static long spanKey(int start, int end) {
        return ((long) start << 32) | (end & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------
    // Source filtering
    // ------------------------------------------------------------------

    /**
     * Keep only entities whose source is in the allowed set.
     * Entities with a null source (OLS labels) are always kept.
     */
    private List<TaggedEntity> applySourceFilter(List<TaggedEntity> entities, List<String> sources) {
        if (sources == null || sources.isEmpty()) return entities;
        Set<String> allowed = new HashSet<>(sources);
        List<TaggedEntity> filtered = new ArrayList<>();
        for (TaggedEntity e : entities) {
            if (e.source == null || allowed.contains(e.source)) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    // ------------------------------------------------------------------
    // Min-length filtering
    // ------------------------------------------------------------------

    /**
     * Remove entities whose matched span is shorter than {@code minLength}.
     */
    private List<TaggedEntity> applyMinLength(List<TaggedEntity> entities, int minLength) {
        if (minLength <= 0) return entities;
        List<TaggedEntity> filtered = new ArrayList<>();
        for (TaggedEntity e : entities) {
            if ((e.end - e.start) >= minLength) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    // ------------------------------------------------------------------
    // Substring removal
    // ------------------------------------------------------------------

    /**
     * Remove entities whose span is strictly contained within another
     * entity's span.  When two entities have identical spans they are
     * both kept (they are not substrings of each other).
     */
    private List<TaggedEntity> removeSubstrings(List<TaggedEntity> entities) {
        if (entities.size() <= 1) return entities;

        // Sort by start asc, then by span length desc so longer spans come first
        List<TaggedEntity> sorted = new ArrayList<>(entities);
        sorted.sort((a, b) -> {
            int cmp = Integer.compare(a.start, b.start);
            if (cmp != 0) return cmp;
            return Integer.compare((b.end - b.start), (a.end - a.start));
        });

        List<TaggedEntity> result = new ArrayList<>();
        for (TaggedEntity candidate : sorted) {
            boolean isSubstring = false;
            for (TaggedEntity kept : result) {
                // candidate is strictly contained within kept
                if (kept.start <= candidate.start && kept.end >= candidate.end
                        && (kept.start < candidate.start || kept.end > candidate.end)) {
                    isSubstring = true;
                    break;
                }
            }
            if (!isSubstring) {
                result.add(candidate);
            }
        }
        return result;
    }
}
