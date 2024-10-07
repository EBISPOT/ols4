package uk.ac.ebi.ols.shared;

public enum DefinedFields {
    BASE_URI("baseUri", "baseUris",
            "The beginning of URIs that OLS assumes to belong to an ontology."),
    DEFINITION("definition", "description","The definition of this entity."),
    HAS_DIRECT_CHILDREN("hasDirectChildren", "has_children",
            "Whether this class has direct children or not."),
    HAS_DIRECT_PARENTS("hasDirectParents", "",
            "Indicates whether this class has direct parents or not."),
    HAS_HIERARCHICAL_CHILDREN("hasHierarchicalChildren", "",
            "Whether this class has hierarchical children or not."),
    HAS_HIERARCHICAL_PARENTS("hasHierarchicalParents", "",
            "Whether this class has hierarchical parents or not."),
    HAS_INDIVIDUALS("hasIndividuals", "", ""),
    HAS_LOCAL_DEFINITION("hasLocalDefinition", "",
            "True if term is definined within this ontology."),
    IMPORTED("imported", "",
            "Whether this entity is imported or not."),
    IS_DEFINING_ONTOLOGY("isDefiningOntology",
            "is_defining_ontology","Whether this entity is defined in this ontology or not."),
    IS_OBSOLETE("isObsolete", "isObsolete",
                        "Set to true if this entity is obsolete, otherwise is set to false."),
    IS_PREFERRED_ROOT("isPreferredRoot", "is_preferred_root",
            "Set to true if this entity is a preferred root or not."),
    NUM_DESCENDANTS ("numDescendants", "", "Number of descendants of this entity."),
    NUM_HIERARCHICAL_DESCENDANTS ("numHierarchicalDescendants", "",
            "Number of hierarchical descendants of this entity."),;

    private final String text;
    private final String ols3Text;
    private final String description;

    DefinedFields(String text, String ols3Text, String description) {
        this.text = text;
        this.ols3Text = ols3Text;
        this.description = description;
    }

    public String getText() {
        return text;
    }

    public String getOls3Text() {
        return ols3Text;
    }
}
