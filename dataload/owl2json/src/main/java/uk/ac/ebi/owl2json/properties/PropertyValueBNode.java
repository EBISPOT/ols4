package uk.ac.ebi.owl2json.properties;

public class PropertyValueBNode extends PropertyValue {

    String id;

    public PropertyValueBNode(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public Type getType() {
        return Type.BNODE;
    }
    
    public boolean equals(PropertyValue other) {
        return other.getType() == Type.BNODE && ((PropertyValueBNode) other).id.equals(id);
    }
}
