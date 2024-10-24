package uk.ac.ebi.ols.shared;

public enum DefinedFields {
    APPEARS_IN("appearsIn", "", "The list of ontologies in which the current entity is used."),
    BASE_URI("baseUri", "baseUris",
            "The beginning of URIs that OLS assumes to belong to an ontology."),
    DEFINED_BY("definedBy", "", "A list of ontologies which defines this term"),
    DEFINITION("definition", "description","The definition of this entity."),
    DIRECT_ANCESTOR("directAncestor", "",
            "A list of all direct parents by subclass- and hierarchical relations for this entity."),
    DIRECT_PARENT("directParent", "","A list of the direct parents of this entity."),
    EXPORTS_TO("exportsTo", "", "Lists the prefixes of ontologies that imports this ontology."),
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
    HIERARCHICAL_ANCESTOR("hierarchicalAncestor","","The list of ancestors of this entity via " +
            "subclass relationships and hierarchical properties such as part_of(BFO:0000050) relations."),
    HIERARCHICAL_PARENT("hierarchicalParent","" ,"The list of parents of this entity via " +
            "subclass relationships and hierarchical properties such as part_of(BFO:0000050) relations."),
    IMPORTED("imported", "",
            "Whether this entity is imported or not."),
    IMPORTS_FROM("importsFrom", "",
            "Lists the prefixes of the ontologies the current ontology imports from."),
    IS_DEFINING_ONTOLOGY("isDefiningOntology",
            "is_defining_ontology","Whether this entity is defined in this ontology or not."),
    IS_OBSOLETE("isObsolete", "isObsolete",
                        "Set to true if this entity is obsolete, otherwise is set to false."),
    IS_PREFERRED_ROOT("isPreferredRoot", "is_preferred_root",
            "Set to true if this entity is a preferred root or not."),
    LABEL("label", "", "The name or names of this entity."),
    LANGUAGE("language", "", "The language or languages this ontology is available in."),
    NUM_DESCENDANTS ("numDescendants", "", "Number of descendants of this entity."),
    NUM_HIERARCHICAL_DESCENDANTS ("numHierarchicalDescendants", "",
            "Number of hierarchical descendants of this entity."),
    PREFERRED_ROOT("preferredRoot", "",
            "A list of entities that serve as roots for this ontology."),
    RELATED_FROM("relatedFrom","", "The list of classes in which this class is used as part of its definition. " +
            "I.e. 'liver disease' (EFO:0001421) has a relatedFrom relation with 'serum albumin measurement' (EFO:0004535) because " +
            "'serum albumin measurement' is a subclass of 'is about some liver disease'."),
    RELATED_TO("relatedTo","", "The list of classes that are used in axioms in the filler position" +
            " in defining this class. It only considers classes in the filler position, not classes expressions in the filler position." +
            "I.e. 'liver disease' (EFO:0001421) has a relatedTo relation with 'liver' (UBERON:0002107) because " +
            "'liver disease' is a subclass of 'has_disease_location some liver'."),
    SYNONYM("synonym", "", "The list of names that are synonyms of this entity.");

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
