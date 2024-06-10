package uk.ac.ebi.spot.ols.model;

/**
 * @author Erhun Giray TUNCAY
 * @email giray.tuncay@tib.eu
 * TIB-Leibniz Information Center for Science and Technology
 */
public enum SKOSRelation {

    broader("http://www.w3.org/2004/02/skos/core#broader"),

    narrower("http://www.w3.org/2004/02/skos/core#narrower"),

    related("http://www.w3.org/2004/02/skos/core#related"),

    hasTopConcept("http://www.w3.org/2004/02/skos/core#hasTopConcept"),

    topConceptOf("http://www.w3.org/2004/02/skos/core#topConceptOf");

    private final String propertyName;

    SKOSRelation(String propertyName) {
        this.propertyName = propertyName;
    }

    public static String[] getNames() {
        String[] commands = new String[SKOSRelation.values().length];
        for (int i = 0;i<SKOSRelation.values().length;i++) {
            commands[i] = SKOSRelation.values()[i].getPropertyName();
        }
        return commands;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
