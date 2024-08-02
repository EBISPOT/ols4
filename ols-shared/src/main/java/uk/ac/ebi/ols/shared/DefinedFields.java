package uk.ac.ebi.ols.shared;

public enum DefinedFields {
    IS_OBSOLETE("isObsolete", "is_obsolete", "Set to true if this entity is obsolete, otherwise is set to false."),
    DEFINITION("definition", "description","The definition of this entity."),
    DIRECT_PARENT("directParent", "parents","The list of direct parents for this entity.");

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
