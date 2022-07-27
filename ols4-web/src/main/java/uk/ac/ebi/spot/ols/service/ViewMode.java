package uk.ac.ebi.spot.ols.service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum ViewMode {

    ALL("All", "", "All terms"),
    PREFERRED_ROOTS("PreferredRoots", "PreferredRootTerm", "Preferred root terms");

    private final String shortName;
    private final String neo4jLabel;
    private final String displayString;

    private static final Map<String, ViewMode> ENUM_MAP;

    private ViewMode(String shortName, String neo4jLabel, String displayString) {
        this.shortName = shortName;
        this.neo4jLabel = neo4jLabel;
        this.displayString = displayString;
    }

    public String getShortName() {
        return shortName;
    }

    public String getNeo4jLabel() {
        return neo4jLabel;
    }

    public String getDisplayString() {
        return displayString;
    }

    static {
        Map<String, ViewMode> map = new ConcurrentHashMap<String, ViewMode>();

        for (ViewMode instance : ViewMode.values()) {
            map.put(instance.getShortName(), instance);
        }
        ENUM_MAP = Collections.unmodifiableMap(map);
    }

    public static ViewMode getFromShortName(String shortName) {
        return ENUM_MAP.get(shortName);
    }
}
