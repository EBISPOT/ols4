package uk.ac.ebi.owl2json.properties;

public class PropertyValueID extends PropertyValue {

    String id;

    public PropertyValueID(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public Type getType() {
        return Type.ID;
    }
    
    public boolean equals(PropertyValue other) {
        return other.getType() == Type.ID && ((PropertyValueID) other).id.equals(id);
    }
}
