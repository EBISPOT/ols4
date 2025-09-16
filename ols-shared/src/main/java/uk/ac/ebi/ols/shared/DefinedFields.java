package uk.ac.ebi.ols.shared;

public enum DefinedFields {
    APPEARS_IN("appearsIn", "", "The list of ontologies in which the current entity is used.", "array"),
    BASE_URI("baseUri", "baseUris",
            "The beginning of URIs that OLS assumes to belong to an ontology.", "array"),
    DEFINED_BY("definedBy", "", "A list of ontologies which defines this term", "array"),
    DEFINITION("definition", "description","The definition of this entity.", "string"),
    DIRECT_ANCESTOR("directAncestor", "",
            "A list of all direct parents by subclass- and hierarchical relations for this entity.", "array"),
    DIRECT_PARENT("directParent", "","A list of the direct parents of this entity.", "array"),
    EXPORTS_TO("exportsTo", "", "Lists the prefixes of ontologies that imports this ontology.", "array"),
    HAS_DIRECT_CHILDREN("hasDirectChildren", "has_children",
            "Whether this class has direct children or not.", "boolean"),
    HAS_DIRECT_PARENTS("hasDirectParents", "",
            "Indicates whether this class has direct parents or not.", "boolean"),
    HAS_HIERARCHICAL_CHILDREN("hasHierarchicalChildren", "",
            "Whether this class has hierarchical children or not.", "boolean"),
    HAS_HIERARCHICAL_PARENTS("hasHierarchicalParents", "",
            "Whether this class has hierarchical parents or not.", "boolean"),
    HAS_INDIVIDUALS("hasIndividuals", "", "Whether individuals exists or not.", "boolean"),
    HAS_LOCAL_DEFINITION("hasLocalDefinition", "",
            "True if term is definined within this ontology.", "boolean"),
    HIERARCHICAL_ANCESTOR("hierarchicalAncestor","","The list of ancestors of this entity via " +
            "subclass relationships and hierarchical properties such as part_of(BFO:0000050) relations.", "array"),
    HIERARCHICAL_PARENT("hierarchicalParent","" ,"The list of parents of this entity via " +
            "subclass relationships and hierarchical properties such as part_of(BFO:0000050) relations.", "array"),
    IMPORTED("imported", "",
            "Whether this entity is imported or not.", "boolean"),
    IMPORTS_FROM("importsFrom", "",
            "Lists the prefixes of the ontologies the current ontology imports from.", "array"),
    IS_DEFINING_ONTOLOGY("isDefiningOntology",
            "is_defining_ontology","Whether this entity is defined in this ontology or not.", "boolean"),
    IS_OBSOLETE("isObsolete", "isObsolete",
                        "Set to true if this entity is obsolete, otherwise is set to false.", "boolean"),
    IS_PREFERRED_ROOT("isPreferredRoot", "is_preferred_root",
            "Set to true if this entity is a preferred root or not.", "boolean"),
    LABEL("label", "", "The name or names of this entity.", "string"),
    LANGUAGE("language", "", "The language or languages this ontology is available in.", "array"),
    MAILING_LIST("mailingList", "", "The mailing list for the ontology.", "string"),
    NUM_DESCENDANTS ("numDescendants", "", "Number of descendants of this entity.", "integer"),
    NUM_HIERARCHICAL_DESCENDANTS ("numHierarchicalDescendants", "",
            "Number of hierarchical descendants of this entity.", "integer"),
    ONTOLOGY_PURL("ontologyPurl", "", "The URL of the ontology to download the ontology file.", "string"),
    PREFERRED_ROOT("preferredRoot", "",
            "A list of entities that serve as roots for this ontology.", "array"),
    RELATED_FROM("relatedFrom","", "The list of classes in which this class is used as part of its definition. " +
            "I.e. 'liver disease' (EFO:0001421) has a relatedFrom relation with 'serum albumin measurement' (EFO:0004535) because " +
            "'serum albumin measurement' is a subclass of 'is about some liver disease'.", "array"),
    RELATED_TO("relatedTo","", "The list of classes that are used in axioms in the filler position" +
            " in defining this class. It only considers classes in the filler position, not classes expressions in the filler position." +
            "I.e. 'liver disease' (EFO:0001421) has a relatedTo relation with 'liver' (UBERON:0002107) because " +
            "'liver disease' is a subclass of 'has_disease_location some liver'.", "array"),
    SYNONYM("synonym", "", "The list of names that are synonyms of this entity.", "array");

    private final String text;
    private final String ols3Text;
    private final String description;
    private final String type;

    DefinedFields(String text, String ols3Text, String description, String type) {
        this.text = text;
        this.ols3Text = ols3Text;
        this.description = description;
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public String getOls3Text() {
        return ols3Text;
    }

    public String getDescription() { return description; }

    public String getType() { return type; }
}
